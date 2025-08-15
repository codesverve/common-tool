//package com.uetty.common.tool.core.mybatisplus;
//
//import com.baomidou.mybatisplus.core.config.GlobalConfig;
//import com.baomidou.mybatisplus.core.injector.AbstractMethod;
//import com.baomidou.mybatisplus.core.injector.DefaultSqlInjector;
//import com.baomidou.mybatisplus.core.injector.methods.*;
//import com.baomidou.mybatisplus.core.metadata.TableInfo;
//import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
//import org.apache.ibatis.session.Configuration;
//
//import java.util.List;
//import java.util.stream.Stream;
//
//import static java.util.stream.Collectors.toList;
//
//public class CustomSqlInjector extends DefaultSqlInjector {
//
//    @Override
//    public List<AbstractMethod> getMethodList(Configuration configuration, Class<?> mapperClass, TableInfo tableInfo) {
//        GlobalConfig.DbConfig dbConfig = GlobalConfigUtils.getDbConfig(configuration);
//        Stream.Builder<AbstractMethod> builder = Stream.<AbstractMethod>builder()
//                .add(new Insert(dbConfig.isInsertIgnoreAutoIncrementColumn()))
//                .add(new Delete())
//                .add(new Update())
//                .add(new SelectCount())
//                .add(new SelectMaps())
//                .add(new SelectObjs())
//                .add(new SelectList());
//        if (tableInfo.havePK()) {
//            builder.add(new DeleteById())
//                    .add(new DeleteByIds())
//                    .add(new UpdateById())
//                    .add(new SelectById())
//                    .add(new SelectBatchByIds())
//                    .add(new UpdateAllById());
//        } else {
//            logger.warn(String.format("%s ,Not found @TableId annotation, Cannot use Mybatis-Plus 'xxById' Method.",
//                    tableInfo.getEntityType()));
//        }
//        return builder.build().collect(toList());
//    }
//}
