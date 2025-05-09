//package com.cqfy.xxl.job.core.glue.impl;
//
//import com.cqfy.xxl.job.core.executor.impl.XxlJobSpringExecutor;
//import com.cqfy.xxl.job.core.glue.GlueFactory;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.annotation.Qualifier;
//import org.springframework.core.annotation.AnnotationUtils;
//
//import javax.annotation.Resource;
//import java.lang.reflect.Field;
//import java.lang.reflect.Modifier;
//
//
//public class SpringGlueFactory extends GlueFactory {
//
//    private static Logger logger = LoggerFactory.getLogger(SpringGlueFactory.class);
//
//
//    @Override
//    public void injectService(Object instance){
//        if (instance==null) {
//            return;
//        }
//
//        if (XxlJobSpringExecutor.getApplicationContext() == null) {
//            return;
//        }
//
//        Field[] fields = instance.getClass().getDeclaredFields();
//        for (Field field : fields) {
//            if (Modifier.isStatic(field.getModifiers())) {
//                continue;
//            }
//
//            Object fieldBean = null;
//            if (AnnotationUtils.getAnnotation(field, Resource.class) != null) {
//                try {
//                    Resource resource = AnnotationUtils.getAnnotation(field, Resource.class);
//                    if (resource.name()!=null && resource.name().length()>0){
//                        fieldBean = XxlJobSpringExecutor.getApplicationContext().getBean(resource.name());
//                    } else {
//                        fieldBean = XxlJobSpringExecutor.getApplicationContext().getBean(field.getName());
//                    }
//                } catch (Exception e) {
//                }
//                if (fieldBean==null ) {
//                    fieldBean = XxlJobSpringExecutor.getApplicationContext().getBean(field.getType());
//                }
//            }
//            else if (AnnotationUtils.getAnnotation(field, Autowired.class) != null) {
//                Qualifier qualifier = AnnotationUtils.getAnnotation(field, Qualifier.class);
//                if (qualifier!=null && qualifier.value()!=null && qualifier.value().length()>0) {
//                    fieldBean = XxlJobSpringExecutor.getApplicationContext().getBean(qualifier.value());
//                } else {
//                    fieldBean = XxlJobSpringExecutor.getApplicationContext().getBean(field.getType());
//                }
//            }
//            if (fieldBean!=null) {
//                field.setAccessible(true);
//                try {
//                    field.set(instance, fieldBean);
//                } catch (IllegalArgumentException e) {
//                    logger.error(e.getMessage(), e);
//                } catch (IllegalAccessException e) {
//                    logger.error(e.getMessage(), e);
//                }
//            }
//        }
//    }
//
//}
