package com.cqfy.xxl.job.admin.core.trigger;

import com.cqfy.xxl.job.admin.core.conf.XxlJobAdminConfig;
import com.cqfy.xxl.job.admin.core.model.XxlJobGroup;
import com.cqfy.xxl.job.admin.core.model.XxlJobInfo;
import com.cqfy.xxl.job.admin.core.scheduler.XxlJobScheduler;
import com.cqfy.xxl.job.admin.core.util.I18nUtil;
import com.cqfy.xxl.job.core.biz.model.ReturnT;
import com.cqfy.xxl.job.core.biz.model.TriggerParam;
import com.cqfy.xxl.job.core.util.GsonTool;
import com.cqfy.xxl.job.core.util.ThrowableUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/**
 * @author:halfmoonly
 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
 * @Date:2023/7/3
 * @Description:该类也是xxl-job中很重要的一个类，job的远程调用就是在该类中进行的，当然不是直接进行，远程调用
 * 到最后，任务还是在执行器那端执行，但是该类会为远程调用做很多必要的辅助性工作，比如选择路由策略，然后选择要执行
 * 任务的执行器的地址。现在这些功能我们都还没有引入，只是用最简单的，选择第一个服务实例地址进行远程调用
 */
public class XxlJobTrigger {

    private static Logger logger = LoggerFactory.getLogger(XxlJobTrigger.class);

    /**
     * @author:halfmoonly
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/7/3
     * @Description:该方法是远程调用前的准备阶段，在该方法内，如果用户自己设置了执行器的地址和执行器的任务参数，
     * 以及分片策略，在该方法内会对这些操作进行处理
     */
    public static void trigger(int jobId,
                               TriggerTypeEnum triggerType,
                               int failRetryCount,
                               String executorShardingParam,
                               String executorParam,
                               String addressList) {

        //根据任务id，从数据库中查询到该任务的完整信息
        XxlJobInfo jobInfo = XxlJobAdminConfig.getAdminConfig().getXxlJobInfoDao().loadById(jobId);
        //如果任务为null，则打印一条告警信息
        if (jobInfo == null) {
            logger.warn(">>>>>>>>>>>> trigger fail, jobId invalid，jobId={}", jobId);
            return;
        }
        //如果用户在页面选择执行任务的时候，传递参数进来了，这时候就把任务参数设置到job中
        if (executorParam != null) {
            //设置执行器的任务参数
            jobInfo.setExecutorParam(executorParam);
        }
        //同样是根据jobId获取所有的执行器组
        XxlJobGroup group = XxlJobAdminConfig.getAdminConfig().getXxlJobGroupDao().load(jobInfo.getJobGroup());
        //这里也有一个小判断，如果用户在web界面输入了执行器的地址，这里会把执行器的地址设置到刚才查询到的执行器中
        //注意，这里我想强调两点，第一，这里以及上面那个设置执行器参数，都是在web界面对任务进行执行一次操作时，才会出现的调用流程
        //这个大家要弄清楚
        //第二点要强调的就是，XxlJobGroup这个对象，它并不是说里面有集合还是还是什么，在真正的生产环境中，一个定时任务不可能
        //只有一个服务器在执行吧，显然会有多个服务器在执行，对于相同的定时任务，注册到XXL-JOB的服务器上时，会把相同定时任务
        //的服务实例地址规整到一起，就赋值给XxlJobGroup这个类的addressList成员变量，不同的地址用逗号分隔即可
        if (addressList!=null && addressList.trim().length()>0) {
            //这里是设置执行器地址的注册方式，0是自动注册，就是1是用户手动注册的
            group.setAddressType(1);
            group.setAddressList(addressList.trim());
        }
        //执行触发器任务，这里有几个参数我直接写死了，因为现在还用不到，为了不报错，我们就直接写死
        //这里写死的都是沿用源码中设定的默认值
        //其实这里的index和total参数分别代表分片序号和分片总数的意思，但现在我们没有引入分片，只有一台执行器
        //执行定时任务，所以分片序号为0，分片总是为1。
        //分片序号代表的是执行器，如果有三个执行器，那分片序号就是0，1，2
        //分片总数就为3，这里虽然有这两个参数，实际上在第一个版本还用不到。之所以不把参数略去是因为，这样一来
        //需要改动的地方就有点多了，大家理解一下
        //在该方法内，会真正开始远程调用，这个方法，也是远程调用的核心方法
        processTrigger(group, jobInfo, -1, triggerType, 0, 1);
    }


    /**
     * @author:halfmoonly
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/7/3
     * @Description:该方法会判断字符串的内容是不是数字
     */
    private static boolean isNumeric(String str){
        try {
            int result = Integer.valueOf(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }


    /**
     * @author:halfmoonly
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/7/3
     * @Description:在该方法中会进一步处理分片和路由策略
     */
    private static void processTrigger(XxlJobGroup group, XxlJobInfo jobInfo, int finalFailRetryCount, TriggerTypeEnum triggerType, int index, int total){
        //初始化触发器参数，这里的这个触发器参数，是要在远程调用的另一端，也就是执行器那一端使用的
        TriggerParam triggerParam = new TriggerParam();
        //设置任务id
        triggerParam.setJobId(jobInfo.getId());
        //设置执行器要执行的任务的方法名称
        triggerParam.setExecutorHandler(jobInfo.getExecutorHandler());
        //把执行器要执行的任务的参数设置进去
        triggerParam.setExecutorParams(jobInfo.getExecutorParam());
        //设置执行模式，一般都是bean模式
        triggerParam.setGlueType(jobInfo.getGlueType());
        //接下来要再次设定远程调用的服务实例的地址
        //这里其实是考虑到了路由策略，但是第一版本还不涉及这些知识，所以就先不这么做了
        String address = null;
        //得到所有注册到服务端的执行器的地址，并且做判空处理
        List<String> registryList = group.getRegistryList();
        if (registryList!=null && !registryList.isEmpty()) {
            //在源码中，本来这里就要使用路由策略，选择具体的执行器地址了，但是现在我们还没有引入路由策略
            //所以这里就简单处理，就使用集合中的第一个地址
            address = registryList.get(0);
        }
        //接下来就定义一个远程调用的结果变量
        ReturnT<String> triggerResult = null;
        //如果地址不为空
        if (address != null) {
            //在这里进行远程调用，这里就是最核心远程调用的方法，但是方法内部的逻辑很简单，就是用http发送调用
            //消息而已
            triggerResult = runExecutor(triggerParam, address);
            //这里就输出一下状态码吧，根据返回的状态码判断任务是否执行成功
            logger.info("返回的状态码"+triggerResult.getCode());
        } else {
            triggerResult = new ReturnT<String>(ReturnT.FAIL_CODE, null);
        }
    }


    /**
     * @author:halfmoonly
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/7/4
     * @Description:该方法内进行远程调用
     */
    public static ReturnT<String> runExecutor(TriggerParam triggerParam, String address){
        //在这个方法中把消息发送给定时任务执行程序
        HttpURLConnection connection = null;
        BufferedReader bufferedReader = null;
        try {
            //创建链接
            URL realUrl = new URL(address);
            //得到连接
            connection = (HttpURLConnection) realUrl.openConnection();
            //设置连接属性
            //post请求
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setUseCaches(false);
            connection.setReadTimeout(3 * 1000);
            connection.setConnectTimeout(3 * 1000);
            connection.setRequestProperty("connection", "Keep-Alive");
            connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
            connection.setRequestProperty("Accept-Charset", "application/json;charset=UTF-8");
            //进行连接
            connection.connect();
            //判断请求体是否为null
            if (triggerParam != null) {
                //序列化请求体，也就是要发送的触发参数
                String requestBody = GsonTool.toJson(triggerParam);
                //下面就开始正式发送消息了
                DataOutputStream dataOutputStream = new DataOutputStream(connection.getOutputStream());
                dataOutputStream.write(requestBody.getBytes("UTF-8"));
                //刷新缓冲区
                dataOutputStream.flush();
                //释放资源
                dataOutputStream.close();
            }
            //获取响应码
            int statusCode = connection.getResponseCode();
            if (statusCode != 200) {
                //设置失败结果
                return new ReturnT<String>(ReturnT.FAIL_CODE, "xxl-job remoting fail, StatusCode("+ statusCode +") invalid. for url : " + realUrl);
            }
            //下面就开始接收返回的结果了
            bufferedReader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
            StringBuilder result = new StringBuilder();
            String line;
            //接收返回信息
            while ((line = bufferedReader.readLine()) != null) {
                result.append(line);
            }
            //转换为字符串
            String resultJson = result.toString();
            try {
                //转换为ReturnT对象，返回给用户
                ReturnT returnT = GsonTool.fromJson(resultJson, ReturnT.class);
                return returnT;
            } catch (Exception e) {
                logger.error("xxl-job remoting (url="+realUrl+") response content invalid("+ resultJson +").", e);
                return new ReturnT<String>(ReturnT.FAIL_CODE, "xxl-job remoting (url="+realUrl+") response content invalid("+ resultJson +").");
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return new ReturnT<String>(ReturnT.FAIL_CODE, "xxl-job remoting error("+ e.getMessage() +"), for url : " );
        } finally {
            try {
                if (bufferedReader != null) {
                    bufferedReader.close();
                }
                if (connection != null) {
                    connection.disconnect();
                }
            } catch (Exception e2) {
                logger.error(e2.getMessage(), e2);
            }
        }
    }

}
