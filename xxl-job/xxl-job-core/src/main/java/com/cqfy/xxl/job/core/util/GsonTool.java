package com.cqfy.xxl.job.core.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;


/**
 * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
 * @Date:2023/7/12
 * @Description:实体类和json转换的工具类
 */
public class GsonTool {

    private static Gson gson = null;
    static {
            gson= new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
    }


    public static String toJson(Object src) {
        return gson.toJson(src);
    }


    public static <T> T fromJson(String json, Class<T> classOfT) {
        return gson.fromJson(json, classOfT);
    }


    public static <T> T fromJson(String json, Class<T> classOfT, Class argClassOfT) {
        Type type = new ParameterizedType4ReturnT(classOfT, new Class[]{argClassOfT});
        return gson.fromJson(json, type);
    }



    public static class ParameterizedType4ReturnT implements ParameterizedType {
        private final Class raw;
        private final Type[] args;
        public ParameterizedType4ReturnT(Class raw, Type[] args) {
            this.raw = raw;
            this.args = args != null ? args : new Type[0];
        }
        @Override
        public Type[] getActualTypeArguments() {
            return args;
        }
        @Override
        public Type getRawType() {
            return raw;
        }
        @Override
        public Type getOwnerType() {return null;}
    }


    public static <T> List<T> fromJsonList(String json, Class<T> classOfT) {
        return gson.fromJson(
                json,
                new TypeToken<List<T>>() {
                }.getType()
        );
    }

}
