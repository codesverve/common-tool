//package com.uetty.common.tool.core.mybatisplus;
//
//import com.baomidou.mybatisplus.core.injector.AbstractMethod;
//import com.baomidou.mybatisplus.core.metadata.TableInfo;
//import com.baomidou.mybatisplus.core.toolkit.sql.SqlScriptUtils;
//import org.apache.ibatis.mapping.MappedStatement;
//import org.apache.ibatis.mapping.SqlSource;
//
//import java.util.Objects;
//
//import static java.util.stream.Collectors.joining;
//
//public class UpdateAllById extends AbstractMethod {
//
//    private static final String SQL_UPDATE_ALL_BY_ID = "<script>\nUPDATE %s %s WHERE %s=#{%s} %s\n</script>";
//    private static final String METHOD_NAME = "updateAllById";
//
//    protected UpdateAllById() {
//        super(METHOD_NAME);
//    }
//
//    private String sqlSetNoIf(boolean logic, TableInfo table, final String alias,
//                              final String prefix) {
//        String sqlScript = getAllSqlSet(table, logic, prefix);
//        return SqlScriptUtils.convertSet(sqlScript);
//    }
//
//    private String getAllSqlSet(TableInfo table, boolean ignoreLogicDelFiled, final String prefix) {
//        final String newPrefix = prefix == null ? EMPTY : prefix;
//        return table.getFieldList().stream()
//                .filter(i -> {
//                    if (ignoreLogicDelFiled) {
//                        return !(table.isWithLogicDelete() && i.isLogicDelete());
//                    }
//                    return true;
//                }).map(i -> i.getSqlSet(true, newPrefix)).filter(Objects::nonNull).collect(joining(NEWLINE));
//    }
//
//    @Override
//    public MappedStatement injectMappedStatement(Class<?> mapperClass, Class<?> modelClass, TableInfo tableInfo) {
//        final String additional = optlockVersion(tableInfo) + tableInfo.getLogicDeleteSql(true, true);
//        String sql = String.format(SQL_UPDATE_ALL_BY_ID, tableInfo.getTableName(),
//                sqlSetNoIf(tableInfo.isWithLogicDelete(), tableInfo, ENTITY, ENTITY_DOT),
//                tableInfo.getKeyColumn(), ENTITY_DOT + tableInfo.getKeyProperty(), additional);
//        SqlSource sqlSource = super.createSqlSource(configuration, sql, modelClass);
//        return addUpdateMappedStatement(mapperClass, modelClass, methodName, sqlSource);
//    }
//}
