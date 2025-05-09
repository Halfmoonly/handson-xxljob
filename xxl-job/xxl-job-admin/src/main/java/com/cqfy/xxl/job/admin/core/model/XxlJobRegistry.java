package com.cqfy.xxl.job.admin.core.model;

import java.util.Date;

/**
 * @author:halfmoonly
 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
 * @Date:2023/7/11
 * @Description:调度中心持有注册过来的执行器的实体类
 *
 *
 * xxl_job_registry表中的registry_value字段用于存储单个执行器实例的地址（例如IP和端口），
 * 分布式环境中支持多副本执行器，则会在该表中增加多条同执行器的记录，只是registryValue不同
 */
public class XxlJobRegistry {
    //执行器id
    private int id;
    //执行器的注册方法，是手动还是自动
    private String registryGroup;
    //执行器的appName
    private String registryKey;
    //执行器的地址
    private String registryValue;
    //更新时间
    private Date updateTime;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getRegistryGroup() {
        return registryGroup;
    }

    public void setRegistryGroup(String registryGroup) {
        this.registryGroup = registryGroup;
    }

    public String getRegistryKey() {
        return registryKey;
    }

    public void setRegistryKey(String registryKey) {
        this.registryKey = registryKey;
    }

    public String getRegistryValue() {
        return registryValue;
    }

    public void setRegistryValue(String registryValue) {
        this.registryValue = registryValue;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }
}
