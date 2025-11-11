package com.uetty.common.tool.core.excel;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.EasyExcelFactory;
import com.alibaba.excel.ExcelReader;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.format.DateTimeFormat;
import com.alibaba.excel.annotation.format.NumberFormat;
import com.alibaba.excel.annotation.write.style.ContentFontStyle;
import com.alibaba.excel.annotation.write.style.ContentStyle;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.metadata.Head;
import com.alibaba.excel.metadata.data.DataFormatData;
import com.alibaba.excel.metadata.data.WriteCellData;
import com.alibaba.excel.read.metadata.ReadSheet;
import com.alibaba.excel.write.handler.CellWriteHandler;
import com.alibaba.excel.write.handler.context.CellWriteHandlerContext;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.alibaba.excel.write.metadata.holder.WriteSheetHolder;
import com.alibaba.excel.write.metadata.holder.WriteTableHolder;
import com.alibaba.excel.write.metadata.style.WriteCellStyle;
import com.uetty.common.tool.core.FileUtil;
import com.uetty.common.tool.core.Tuple3;
import com.uetty.common.tool.core.reflect.ReflectUtil;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.core.annotation.AnnotationUtils;

import java.beans.PropertyDescriptor;
import java.io.*;
import java.lang.reflect.Field;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 使用excel表头模版的ExcelWriter
 */
@Slf4j
public class TemplatedExcelWriter<T> {

    /**
     * 模版文件内容
     */
    private final byte[] templateFileBytes;
    /**
     * 表头行数
     */
    private final int headRowNum;
    /**
     * 模版文件表头名称映射
     */
    private final Map<Integer, String> templateHeadNameMap;
    /**
     * 模版文件表头索引映射
     */
    private final int maxColumnIndex;
    /**
     * 模版文件对应的实体类
     */
    private final Class<T> clazz;
    /**
     * 内容格式定义
     */
    private final ColumnConfigMap columnConfigMap;
    /**
     * 忽略的列
     */
    private final Set<String> ignoreProperties;

    /**
     * 构造函数
     * @param templateInputStream 模版文件输入流
     * @param headRowNum          表头行数
     * @param clazz               模版文件对应的实体类
     */
    public TemplatedExcelWriter(InputStream templateInputStream, int headRowNum, Class<T> clazz) {
        this(templateInputStream, headRowNum, clazz, Collections.emptyList());
    }

    public TemplatedExcelWriter(InputStream templateInputStream, int headRowNum, Class<T> clazz, Collection<String> ignoreProperties) {
        try {
            templateFileBytes = FileUtil.readToByte(templateInputStream);
        } catch (IOException e) {
            log.error("excel模版文件读取失败", e);
            throw new RuntimeException("excel模版文件读取失败");
        }
        this.headRowNum = headRowNum;
        this.templateHeadNameMap = readTemplateHeader();
        this.clazz = clazz;
        this.maxColumnIndex = this.templateHeadNameMap.keySet()
                .stream()
                .max(Integer::compareTo)
                .orElse(0);
        this.columnConfigMap = readColumnsConfigMap();
        this.ignoreProperties = new HashSet<>(ignoreProperties);
    }

    private Map<Integer, String> readTemplateHeader() {
        TemplateHeaderReader reader = new TemplateHeaderReader();
        try (ExcelReader excelReader = EasyExcelFactory.read(new ByteArrayInputStream(templateFileBytes)).build()) {
            ReadSheet readSheet = EasyExcelFactory.readSheet(0)
                    .headRowNumber(headRowNum)
                    .registerReadListener(reader)
                    .build();
            excelReader.read(readSheet);
            HashMap<Integer, String> map = new HashMap<>(reader.getHeadNameMap());
            if (map.isEmpty()) {
                log.warn("excel模版文件表头为空，请检查模版");
            }
            return map;
        }
    }

    /**
     * 读取列的标题、属性名、样式配置
     */
    private ColumnConfigMap readColumnsConfigMap() {
        Field[] fields = ReflectUtil.getFields(clazz);
        List<Tuple3<Integer, Field, ExcelProperty>> excelPropertyList = new ArrayList<>();
        for (Field field : fields) {
            ExcelProperty excelProperty = AnnotationUtils.findAnnotation(field, ExcelProperty.class);
            if (excelProperty == null) {
                continue;
            }
            excelPropertyList.add(Tuple3.of(excelProperty.index(), field, excelProperty));
        }

        Map<String, List<Tuple3<Integer, Field, ExcelProperty>>> titleGrouping = excelPropertyList.stream()
                .sorted(Comparator.comparingInt(Tuple3::getVal1))
                .filter(tuple -> {
                    String[] value = tuple.getVal3().value();
                    if (value.length == 0) {
                        return false;
                    }
                    return StringUtils.isNotBlank(value[value.length - 1]);
                })
                .collect(Collectors.groupingBy(tuple -> {
                    String[] value = tuple.getVal3().value();
                    return value[value.length - 1].trim();
                }));

        Map<String, ColumnConfig> propertyNameStyleMap = new HashMap<>();
        Map<Integer, ColumnConfig> columnIndexStyleMap = new HashMap<>();

        // 兼容列标题重复的情况
        Map<String, Integer> titleCount = new HashMap<>();
        for (int idx = 0; idx <= maxColumnIndex; idx++) {
            String templateTitle = templateHeadNameMap.get(idx);
            if (templateTitle == null) {
                // 如果模版中没有这个列
                continue;
            }
            templateTitle = templateTitle.trim();
            List<Tuple3<Integer, Field, ExcelProperty>> titleMetaList = titleGrouping.get(templateTitle);
            if (titleMetaList == null) {
                // 如果这个列没有数据对象属性
                continue;
            }

            // 列标题计数，兼容列标题重复的情况
            int count = titleCount.compute(templateTitle, (key, val) -> val == null ? 1 : val + 1);
            // 如果这个列标题只有1个类属性与其对应，则取第0个，如果这个列标题有多个类属性与其对应，则取第count个类属性
            int index = Math.min(count - 1, titleMetaList.size() - 1);
            Tuple3<Integer, Field, ExcelProperty> propertyMeta = titleMetaList.get(index);

            ColumnConfig columnConfig = buildColumnConfig(idx, templateTitle, propertyMeta.getVal2(), propertyMeta.getVal3());
            propertyNameStyleMap.put(columnConfig.getFieldName(), columnConfig);
            columnIndexStyleMap.put(idx, columnConfig);
        }

        ColumnConfigMap propertyContentConfig = new ColumnConfigMap();
        propertyContentConfig.setPropertyNameStyleMap(propertyNameStyleMap);
        propertyContentConfig.setColumnIndexStyleMap(columnIndexStyleMap);

        if (propertyNameStyleMap.isEmpty() || columnIndexStyleMap.isEmpty()) {
            log.warn("Java数据实体读取到的与模版表头对应的字段为空，请检查Excel数据实体与模版文件");
        }
        return propertyContentConfig;
    }

    private boolean isDateOrTime(Class<?> fieldType) {
        return LocalDateTime.class.isAssignableFrom(fieldType) || LocalDate.class.isAssignableFrom(fieldType)
                || LocalTime.class.isAssignableFrom(fieldType) || Date.class.isAssignableFrom(fieldType);
    }

    private ColumnConfig buildColumnConfig(int index, String title, Field field, ExcelProperty excelProperty) {
        ColumnConfig columnConfig = new ColumnConfig();
        columnConfig.setColumnIndex(index);
        columnConfig.setTitle(title);
        columnConfig.setFieldName(field.getName());

        Class<?> fieldType = field.getType();
        DateTimeFormat dateTimeFormat = AnnotationUtils.findAnnotation(field, DateTimeFormat.class);
        if (dateTimeFormat != null && isDateOrTime(fieldType)) {
            // 日期时间格式化格式
            columnConfig.setDateTimeFormatValue(dateTimeFormat.value());
        }
        // @NumberFormat("#,##0.00")
        // @NumberFormat("0.00%")
        NumberFormat numberFormat = AnnotationUtils.findAnnotation(field, NumberFormat.class);
        if (numberFormat != null) {
            columnConfig.setNumberFormatValue(numberFormat.value());
            columnConfig.setRoundingMode(numberFormat.roundingMode());
        }
        ContentFontStyle contentFontStyle = AnnotationUtils.findAnnotation(field, ContentFontStyle.class);
        if (contentFontStyle != null) {
            columnConfig.setFontName(contentFontStyle.fontName());
            columnConfig.setFontColor(contentFontStyle.color());
        }
        ContentStyle contentStyle = AnnotationUtils.findAnnotation(field, ContentStyle.class);
        if (contentStyle != null) {
            columnConfig.setForegroundColor(contentStyle.fillForegroundColor());
        }
        return columnConfig;
    }

    private Object valueOf(Object value, Class<?> fieldType) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.sql.Date) {
            return new Date(((java.sql.Date) value).getTime());
        }
        if (value instanceof java.sql.Time) {
            return LocalDateTime.of(LocalDate.now(), ((java.sql.Time) value).toLocalTime());
        }
        if (value instanceof java.sql.Timestamp) {
            return new Date(((java.sql.Timestamp) value).getTime());
        }
        if (value instanceof LocalTime) {
            return LocalDateTime.of(LocalDate.now(), (LocalTime) value);
        }
        if (value instanceof CharSequence || value instanceof Number || value instanceof Character
                || value instanceof LocalDate || value instanceof LocalDateTime || value instanceof Date
                || value instanceof Boolean) {
            return value;
        }
        return value;
    }

    private List<Object> getRowValues(T record) {
        List<Object> rowDatas = new ArrayList<>();
        if (record == null) {
            for (int i = 0; i <= maxColumnIndex; i++) {
                rowDatas.add(null);
            }
            return rowDatas;
        }

        BeanWrapper wrapper = new BeanWrapperImpl(record);
        Map<String, Object> fieldNameValueMap = new HashMap<>();
        for (PropertyDescriptor pd : wrapper.getPropertyDescriptors()) {
            String fieldName = pd.getName();
            if ("class".equals(fieldName)) {
                continue;
            }
            ColumnConfig columnConfig = columnConfigMap.getPropertyNameStyleMap().get(fieldName);
            if (columnConfig == null) {
                // Excel文件中不需要这个字段的值
                continue;
            }
            if (ignoreProperties != null && ignoreProperties.contains(fieldName)) {
                continue;
            }

            Object fieldValue = wrapper.getPropertyValue(fieldName);
            fieldValue = valueOf(fieldValue, pd.getPropertyType());
            fieldNameValueMap.put(fieldName, fieldValue);
        }

        for (int colIndex = 0; colIndex <= maxColumnIndex; colIndex++) {
            ColumnConfig columnConfig = columnConfigMap.getColumnIndexStyleMap().get(colIndex);
            if (columnConfig == null) {
                // 如果这个列标题为空，或不存在类属性，则给空值
                rowDatas.add(null);
                continue;
            }

            String fieldName = columnConfig.getFieldName();
            Object fieldValue = fieldNameValueMap.get(fieldName);
            rowDatas.add(fieldValue);
        }
        return rowDatas;
    }

    public void write(List<T> records, File outFile) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        WriteSheet sheet = EasyExcel.writerSheet()
                .build();
        ExcelWriter excelWriter = EasyExcel.write(outputStream)
                .withTemplate(new ByteArrayInputStream(templateFileBytes))
                .autoCloseStream(false)
                .registerWriteHandler(new DataCellWriteHandler())
                .build();

        // 重写数据列顺序
        List<List<Object>> rowList = records.stream()
                .map(this::getRowValues)
                .collect(Collectors.toList());
        excelWriter.write(rowList, sheet).finish();

        try {
            FileUtil.writeToFile(outFile, outputStream.toByteArray(), false);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Getter
    private class TemplateHeaderReader extends AnalysisEventListener<Map<Integer, Object>> {

        private Map<Integer, String> headNameMap;

        @Override
        public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
            Integer rowIndex = context.readRowHolder().getRowIndex();
            if (rowIndex != headRowNum - 1) {
                return;
            }
            headNameMap = new HashMap<>(headMap);
        }

        @Override
        public void invoke(Map<Integer, Object> data, AnalysisContext context) {
        }

        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {
            int maxRowIndex = context.readRowHolder().getRowIndex();
            if (maxRowIndex >= headRowNum) {
                log.warn("模版文件已写入行数超出模版文件表头行数，会影响写入数据的初始行，请检查模版文件是否正确");
            }
        }

    }

    /**
     * 数据行如果有特殊的样式处理需求，后续再在这里扩展样式处理机制
     */
    private class DataCellWriteHandler implements CellWriteHandler {
        private final Map<Integer, CellStyle> cellStyleCache = new HashMap<>();
        private final Map<Integer, DataFormatData> dataFormatDataCache = new HashMap<>();

        private DataFormatData getDataFormatData(Integer columnIndex, ColumnConfig columnConfig) {
            return dataFormatDataCache.computeIfAbsent(columnIndex, index -> {
                DataFormatData newDataFormatData = new DataFormatData();
                newDataFormatData.setFormat(columnConfig.getDateTimeFormatValue());
                return newDataFormatData;
            });
        }
        @Override
        public void beforeCellCreate(CellWriteHandlerContext context) {
            CellWriteHandler.super.beforeCellCreate(context);
        }
        @Override
        public void beforeCellCreate(WriteSheetHolder writeSheetHolder, WriteTableHolder writeTableHolder, Row row, Head head, Integer columnIndex, Integer relativeRowIndex, Boolean isHead) {
            CellWriteHandler.super.beforeCellCreate(writeSheetHolder, writeTableHolder, row, head, columnIndex, relativeRowIndex, isHead);
        }
        @Override
        public void afterCellCreate(CellWriteHandlerContext context) {
            CellWriteHandler.super.afterCellCreate(context);
        }

        private boolean isNeedOverrideStyle(ColumnConfig columnConfig) {
            return columnConfig.getFontName() != null || columnConfig.getFontColor() != null || columnConfig.getForegroundColor() != null
                    || columnConfig.getNumberFormatValue() != null || columnConfig.getDateTimeFormatValue() != null;
        }

        private Font createFont(Font oldFont, Workbook workbook, String fontName, Short fontColor) {
            if (fontName == null && fontColor == null) {
                return oldFont;
            }
            Font font = workbook.createFont();
            font.setFontName(oldFont.getFontName() != null ? oldFont.getFontName() : "宋体");
            font.setFontHeightInPoints(oldFont.getFontHeightInPoints());
            font.setColor(oldFont.getColor());
            font.setBold(oldFont.getBold());
            font.setItalic(oldFont.getItalic());
            font.setUnderline(oldFont.getUnderline());
            font.setStrikeout(oldFont.getStrikeout());
            font.setTypeOffset(oldFont.getTypeOffset());
            font.setCharSet(oldFont.getCharSet());
            if (fontName != null) {
                font.setFontName(fontName);
            }
            if (fontColor != null) {
                font.setColor(fontColor);
            }
            return font;
        }
        @Override
        public void afterCellCreate(WriteSheetHolder writeSheetHolder, WriteTableHolder writeTableHolder, Cell cell, Head head, Integer relativeRowIndex, Boolean isHead) {
            CellWriteHandler.super.afterCellCreate(writeSheetHolder, writeTableHolder, cell, head, relativeRowIndex, isHead);
        }
        @Override
        public void afterCellDataConverted(CellWriteHandlerContext context) {
            CellWriteHandler.super.afterCellDataConverted(context);
        }
        @Override
        public void afterCellDataConverted(WriteSheetHolder writeSheetHolder, WriteTableHolder writeTableHolder, WriteCellData<?> cellData, Cell cell, Head head, Integer relativeRowIndex, Boolean isHead) {
            CellWriteHandler.super.afterCellDataConverted(writeSheetHolder, writeTableHolder, cellData, cell, head, relativeRowIndex, isHead);

            Map<Integer, ColumnConfig> columnIndexStyleMap = columnConfigMap.getColumnIndexStyleMap();
            int columnIndex = cell.getColumnIndex();
            ColumnConfig columnConfig = columnIndexStyleMap.get(columnIndex);
            if (columnConfig == null || !isNeedOverrideStyle(columnConfig)) {
                // 不需要重写样式
                return;
            }

            CellStyle cellStyle = cellStyleCache.get(columnIndex);
            if (cellStyle != null) {
                if (columnConfig.getDateTimeFormatValue() != null) {
                    WriteCellStyle writeCellStyle = cellData.getWriteCellStyle();
                    Object data = cellData.getData();
                    if (data == null || isDateOrTime(data.getClass())) {
                        DataFormatData newDataFormatData = getDataFormatData(columnIndex, columnConfig);
                        writeCellStyle.setDataFormatData(newDataFormatData);
                    }
                }

                cell.setCellStyle(cellStyle);
                return;
            }

            Workbook workbook = writeSheetHolder.getSheet().getWorkbook();
            cellStyle = workbook.createCellStyle();
            CellStyle oldCellStyle = cell.getCellStyle();
            cellStyle.cloneStyleFrom(oldCellStyle);
            if (columnConfig.getForegroundColor() != null) {
                cellStyle.setFillForegroundColor(columnConfig.getForegroundColor());
            }

            int fontIndex = cellStyle.getFontIndex();
            Font oldFont = workbook.getFontAt((short) fontIndex);
            Font font = createFont(oldFont, workbook, columnConfig.getFontName(), columnConfig.getFontColor());
            cellStyle.setFont(font);

            if (columnConfig.getNumberFormatValue() != null) {
                short format = workbook.createDataFormat().getFormat(columnConfig.getNumberFormatValue());
                cellStyle.setDataFormat(format);
            }

            if (columnConfig.getDateTimeFormatValue() != null) {
                WriteCellStyle writeCellStyle = cellData.getWriteCellStyle();
                Object data = cellData.getData();
                if (data == null || isDateOrTime(data.getClass())) {
                    DataFormatData newDataFormatData = getDataFormatData(columnIndex, columnConfig);
                    writeCellStyle.setDataFormatData(newDataFormatData);
                }
            }

            cellStyleCache.put(columnIndex, cellStyle);
            cell.setCellStyle(cellStyle);
        }
        @Override
        public void afterCellDispose(CellWriteHandlerContext context) {
            CellWriteHandler.super.afterCellDispose(context);
        }
        @Override
        public void afterCellDispose(WriteSheetHolder writeSheetHolder, WriteTableHolder writeTableHolder, List<WriteCellData<?>> cellDataList, Cell cell, Head head, Integer relativeRowIndex, Boolean isHead) {
            CellWriteHandler.super.afterCellDispose(writeSheetHolder, writeTableHolder, cellDataList, cell, head, relativeRowIndex, isHead);
        }
    }

    @Data
    private static class ColumnConfigMap {

        private Map<String, ColumnConfig> propertyNameStyleMap;

        private Map<Integer, ColumnConfig> columnIndexStyleMap;

    }

    @Data
    private static class ColumnConfig {
        /**
         * 列位置
         */
        private Integer columnIndex;
        /**
         * 标题
         */
        private String title;
        /**
         * 属性名
         */
        private String fieldName;
        /**
         * 字体名称
         */
        private String fontName;
        /**
         * 字体颜色
         */
        private Short fontColor;
        /**
         * 背景颜色
         */
        private Short foregroundColor;
        /**
         * 数值格式
         */
        private String numberFormatValue;
        /**
         * 数值四舍五入模式
         */
        private RoundingMode roundingMode;
        /**
         * 日期时间格式化格式
         */
        private String dateTimeFormatValue;
    }
}
