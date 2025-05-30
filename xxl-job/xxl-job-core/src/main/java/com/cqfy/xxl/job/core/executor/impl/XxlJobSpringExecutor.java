package com.cqfy.xxl.job.core.executor.impl;

import com.cqfy.xxl.job.core.executor.XxlJobExecutor;
import com.cqfy.xxl.job.core.glue.GlueFactory;
import com.cqfy.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;
import java.util.Map;


/**
 * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
 * @Date:2023/7/8
 * @Description:该类就是执行器这一点服务开始执行的入口。该类中的afterSingletonsInstantiated方法会在IOC容器中
 * 的所有单例bean初始化完毕后被回调，该类的对象会在用户自己创建的XxlJobConfig配置类中，被当作一个bean对象被注入到IOC容器中
 */
public class XxlJobSpringExecutor extends XxlJobExecutor implements ApplicationContextAware, SmartInitializingSingleton, DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(XxlJobSpringExecutor.class);


    /**
     * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/7/8
     * @Description:执行器启动的入口，在该方法内，会把用户定义的定时任务，也就是加了@XxlJob注解的方法，注册到IJobHandler对象中
     *
     */
    @Override
    public void afterSingletonsInstantiated() {

        //该方法就会把用户定义的所有定时任务注册到IJobHandler中，这里的applicationContext是由
        //该类实现的ApplicationContextAware接口帮忙注入的，这个是Spring的基础知识，想必大家应该都清楚
        //这所以需要它，是因为ApplicationContextAware可以得到所有初始化好的单例bean
        initJobHandlerMethodRepository(applicationContext);

        //创建glue工厂，默认使用Spring模式的工厂
        GlueFactory.refreshInstance(1);

        //在这里调用父类的方法启动了执行器
        try {
            super.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    //释放资源的方法，该方法会调用到父类的方法，在父类的方法中
    //其实就是停止了执行器的Netty服务器，停止了每一个工作的JobThread线程
    //逻辑很简答的
    @Override
    public void destroy() {
        super.destroy();
    }



    /**
     * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/7/8
     * @Description:该方法会把用户定义的所有定时任务注册到IJobHandler对象中，其实是MethodJobHandler对象
     * MethodJobHandler对象是IJobHandler的子类
     */
    private void initJobHandlerMethodRepository(ApplicationContext applicationContext) {
        if (applicationContext == null) {
            return;
        }
        //获取IOC容器中所有初始化好的bean的名字，这里的后两个参数都为boolean类型
        //第一个决定查到的对象是否允许为非单例的，传入false，意思为不获得非单例对象
        //第二个意思是查找的对象是否允许为延迟初始化的，就是LazyInit的意思，参数为true，就是允许的意思
        String[] beanDefinitionNames = applicationContext.getBeanNamesForType(Object.class, false, true);
        for (String beanDefinitionName : beanDefinitionNames) {
            //根据名称获得每一个bean
            Object bean = applicationContext.getBean(beanDefinitionName);
            //这里定义的变量就是用来收集bean对象中添加了@XxlJob注解的方法了
            Map<Method, XxlJob> annotatedMethods = null;
            try {
                //下面是Spring自己封装的和反射相关的类，通过下面的操作，可以得到bean对象中添加了@XxlJob注解的方法
                annotatedMethods = MethodIntrospector.selectMethods(bean.getClass(),
                        new MethodIntrospector.MetadataLookup<XxlJob>() {
                            @Override
                            public XxlJob inspect(Method method) {
                                //在这里检查方法是否添加了@XxlJob注解
                                return AnnotatedElementUtils.findMergedAnnotation(method, XxlJob.class);
                            }
                        });
            } catch (Throwable ex) {
                logger.error("xxl-job method-jobhandler resolve error for bean[" + beanDefinitionName + "].", ex);
            }
            //如果结果为空，就说明该bean对象中没有方法添加了@XxlJob注解
            if (annotatedMethods==null || annotatedMethods.isEmpty()) {
                continue;
            }
            //在这里bean对象中每一个添加了@XxlJob注解的方法
            for (Map.Entry<Method, XxlJob> methodXxlJobEntry : annotatedMethods.entrySet()) {
                //得到该方法
                Method executeMethod = methodXxlJobEntry.getKey();
                //得到注解
                XxlJob xxlJob = methodXxlJobEntry.getValue();
                //在这里将该方法注册到JobHandler的子类对象中
                //这时候，逻辑就会跑到父类中了
                registJobHandler(xxlJob, bean, executeMethod);
            }
        }
    }


   //该成员变量就是由下面的set方法注入的
    private static ApplicationContext applicationContext;

    //该方法就是ApplicationContextAware接口中定义的方法
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        XxlJobSpringExecutor.applicationContext = applicationContext;
    }

    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }

}
