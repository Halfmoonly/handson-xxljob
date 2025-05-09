package com.cqfy.xxl.job.core.biz.client;


import com.cqfy.xxl.job.core.biz.AdminBiz;
import com.cqfy.xxl.job.core.biz.model.RegistryParam;
import com.cqfy.xxl.job.core.biz.model.ReturnT;
import com.cqfy.xxl.job.core.util.XxlJobRemotingUtil;

/**
 * @author:Halfmoonly
 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
 * @Date:2023/7/8
 * @Description:该类就是执行器用来访问调度中心的客户端
 */
public class AdminBizClient implements AdminBiz {

    public AdminBizClient() {
    }

    /**
     * @author:Halfmoonly
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/7/8
     * @Description:构造方法
     */
    public AdminBizClient(String addressUrl, String accessToken) {
        this.addressUrl = addressUrl;
        this.accessToken = accessToken;
        if (!this.addressUrl.endsWith("/")) {
            this.addressUrl = this.addressUrl + "/";
        }
    }

    //这里的地址是调度中心的服务地址
    private String addressUrl ;
    //token令牌，执行器和调度中心两端要一致
    private String accessToken;
    //访问超时时间
    private int timeout = 3;



    /**
     * @author:Halfmoonly
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/7/8
     * @Description:调用工具类发送post请求，访问调度中心，这个方法时将执行器注册到调度中心的方法
     */
    @Override
    public ReturnT<String> registry(RegistryParam registryParam) {
        return XxlJobRemotingUtil.postBody(addressUrl + "api/registry", accessToken, timeout, registryParam, String.class);
    }

    /**
     * @author:Halfmoonly
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/7/8
     * @Description:这个方法是通知调度中心，把该执行器移除的方法
     */
    @Override
    public ReturnT<String> registryRemove(RegistryParam registryParam) {
        return XxlJobRemotingUtil.postBody(addressUrl + "api/registryRemove", accessToken, timeout, registryParam, String.class);
    }

}
