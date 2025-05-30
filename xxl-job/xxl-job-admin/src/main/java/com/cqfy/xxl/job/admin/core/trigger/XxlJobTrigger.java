package com.cqfy.xxl.job.admin.core.trigger;

import com.cqfy.xxl.job.admin.core.conf.XxlJobAdminConfig;
import com.cqfy.xxl.job.admin.core.model.XxlJobGroup;
import com.cqfy.xxl.job.admin.core.model.XxlJobInfo;
import com.cqfy.xxl.job.admin.core.model.XxlJobLog;
import com.cqfy.xxl.job.admin.core.route.ExecutorRouteStrategyEnum;
import com.cqfy.xxl.job.admin.core.scheduler.XxlJobScheduler;
import com.cqfy.xxl.job.admin.core.util.I18nUtil;
import com.cqfy.xxl.job.core.biz.ExecutorBiz;
import com.cqfy.xxl.job.core.biz.model.ReturnT;
import com.cqfy.xxl.job.core.biz.model.TriggerParam;
import com.cqfy.xxl.job.core.enums.ExecutorBlockStrategyEnum;
import com.cqfy.xxl.job.core.util.IpUtil;
import com.cqfy.xxl.job.core.util.ThrowableUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;

/**
 * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
 * @Date:2023/7/3
 * @Description:该类也是xxl-job中很重要的一个类，job的远程调用就是在该类中进行的，当然不是直接进行，远程调用
 * 到最后，任务还是在执行器那端执行，但是该类会为远程调用做很多必要的辅助性工作，比如选择路由策略，然后选择要执行
 * 任务的执行器的地址。现在这些功能我们都还没有引入，只是用最简单的，选择第一个服务实例地址进行远程调用
 */
public class XxlJobTrigger {

    private static Logger logger = LoggerFactory.getLogger(XxlJobTrigger.class);

    /**
     * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
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
        //得到用户设定的该任务的失败重试次数
        int finalFailRetryCount = failRetryCount>=0?failRetryCount:jobInfo.getExecutorFailRetryCount();
        //同样是根据jobId获取所有的执行器
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
            //然后把用户在web页面输入的执行器地址覆盖原来的执行器地址
            group.setAddressList(addressList.trim());
        }
        //下面就要处理分片广播的逻辑了
        //先定义一个分片数组
        int[] shardingParam = null;
        //如果用户设定的分片参数不为null，其实这个参数一直都是null，不会给用户设定的机会
        //是程序内部根据用户是否配置了分片广播策略来自动设定分片参数的
        if (executorShardingParam!=null){
            //如果参数不为null，那就将字符串分割一下，分割成两个，
            String[] shardingArr = executorShardingParam.split("/");
            //做一下校验
            if (shardingArr.length==2 && isNumeric(shardingArr[0]) && isNumeric(shardingArr[1])) {
                //在这里初始化数组，容量为2数组的第一个参数就是分片序号，也就是代表的几号执行器，数组第二位就是总的分片数
                //如果现在只有一台执行器在执行，那么数组一号位代表的就是0号执行器，2号位代表的就是只有一个分片，因为只有一个执行器执行任务
                shardingParam = new int[2];
                shardingParam[0] = Integer.valueOf(shardingArr[0]);
                shardingParam[1] = Integer.valueOf(shardingArr[1]);
            }
        }
        //下面就是具体判定用户是否配置了分片广播的路由策略，并且校验执行器组不为空
        if (ExecutorRouteStrategyEnum.SHARDING_BROADCAST==ExecutorRouteStrategyEnum.match(jobInfo.getExecutorRouteStrategy(), null)
                && group.getRegistryList()!=null && !group.getRegistryList().isEmpty()
                && shardingParam==null) {
            //如果配置了该策略，那就遍历执行器组，并且根据执行器组中的所有执行器地址集合的容量来遍历
            //这不就意味着有几个执行器，就要遍历几次吗？
            for (int i = 0; i < group.getRegistryList().size(); i++) {
                //既然是有几个执行器就要遍历几次，那正好就根据这个i去定了执行器在分片数组中的序号，如果是第一个被遍历到的执行器，就是0号执行器，以此类推。。。
                //而总的分片数不就是执行器组中存放执行器地址集合的长度吗？
                //这里就会自动分片，然后告诉所有的执行器，让执行器去执行任务了，这里我想强调一点，让所有执行器都开始执行任务
                //可能很多朋友都觉得让所有执行器都开始执行相同的定时任务，不会出现并发问题吗？理论上是会的，但是定时任务是程序员自己部署的
                //定时任务的逻辑也是程序员自己实现的，这就需要程序员自己在定时任务的逻辑中把并发问题规避了，反正你能从定时任务中
                //得到分片参数，能得到该定时任务具体是哪个分片序号，具体情况可以看本版本代码提供的测试类
                processTrigger(group, jobInfo, finalFailRetryCount, triggerType, i, group.getRegistryList().size());
            }
        } else {
            //如果没有配置分片策略，并且executorShardingParam参数也为null，那就直接用默认的值，说明只有一个执行器要执行任务
            if (shardingParam == null) {
                //所以数组里只有0和1两个元素
                shardingParam = new int[]{0, 1};
            }
            //这里的index和total参数分别代表分片序号和分片总数的意思，如果只有一台执行器
            //执行定时任务，那分片序号为0，分片总是为1。
            //分片序号代表的是执行器，如果有三个执行器，那分片序号就是0，1，2
            //分片总数就为3
            //在该方法内，会真正开始远程调用，这个方法，也是远程调用的核心方法
            processTrigger(group, jobInfo, finalFailRetryCount, triggerType,  shardingParam[0], shardingParam[1]);
        }
    }


    /**
     * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
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
     * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/7/3
     * @Description:在该方法中会进一步处理分片和路由策略
     */
    private static void processTrigger(XxlJobGroup group, XxlJobInfo jobInfo, int finalFailRetryCount, TriggerTypeEnum triggerType, int index, int total){
        //获得定时任务的阻塞策略，默认是串行
        ExecutorBlockStrategyEnum blockStrategy = ExecutorBlockStrategyEnum.match(jobInfo.getExecutorBlockStrategy(), ExecutorBlockStrategyEnum.SERIAL_EXECUTION);
        //得到当前要调度的执行任务的路由策略，默认是没有
        ExecutorRouteStrategyEnum executorRouteStrategyEnum = ExecutorRouteStrategyEnum.match(jobInfo.getExecutorRouteStrategy(), null);
        //判断路由策略是否等于分片广播，如果等于，就把分片参数拼接成字符串
        String shardingParam = (ExecutorRouteStrategyEnum.SHARDING_BROADCAST==executorRouteStrategyEnum)?String.valueOf(index).concat("/").concat(String.valueOf(total)):null;
        //这里就要开始执行和定时任务日志相关的操作了
        //先创建一个日志对象，用于记录该定时任务执行是的一些信息
        XxlJobLog jobLog = new XxlJobLog();
        //记录定时任务的执行器组id
        jobLog.setJobGroup(jobInfo.getJobGroup());
        //设置定时任务的id
        jobLog.setJobId(jobInfo.getId());
        //设置定时任务的触发时间
        jobLog.setTriggerTime(new Date());
        //在这里把定时任务日志保存到数据库中，保存成功之后，定时任务日志的id也就有了
        XxlJobAdminConfig.getAdminConfig().getXxlJobLogDao().save(jobLog);
        logger.debug(">>>>>>>>>>> xxl-job trigger start, jobId:{}", jobLog.getId());
        //初始化触发器参数，这里的这个触发器参数，是要在远程调用的另一端，也就是执行器那一端使用的
        TriggerParam triggerParam = new TriggerParam();
        //设置任务id
        triggerParam.setJobId(jobInfo.getId());
        //设置执行器要执行的任务的方法名称
        triggerParam.setExecutorHandler(jobInfo.getExecutorHandler());
        //把执行器要执行的任务的参数设置进去
        triggerParam.setExecutorParams(jobInfo.getExecutorParam());
        //把阻塞策略设置进去
        triggerParam.setExecutorBlockStrategy(jobInfo.getExecutorBlockStrategy());
        //设置定时任务的超时时间
        triggerParam.setExecutorTimeout(jobInfo.getExecutorTimeout());
        //设置定时任务的日志id
        triggerParam.setLogId(jobLog.getId());
        //设置定时任务的触发时间，这个触发时间就是jobLog刚才设置的那个时间
        triggerParam.setLogDateTime(jobLog.getTriggerTime().getTime());
        //设置执行模式，一般都是bean模式
        triggerParam.setGlueType(jobInfo.getGlueType());
        //设置glue在线编辑的代码内容
        triggerParam.setGlueSource(jobInfo.getGlueSource());
        //设置glue的更新时间
        triggerParam.setGlueUpdatetime(jobInfo.getGlueUpdatetime().getTime());
        //设置分片参数
        triggerParam.setBroadcastIndex(index);
        triggerParam.setBroadcastTotal(total);
        //接下来要再次设定远程调用的服务实例的地址
        //这里其实是考虑到了路由策略
        String address = null;
        ReturnT<String> routeAddressResult = null;
        //得到所有注册到服务端的执行器的地址，并且做判空处理
        List<String> registryList = group.getRegistryList();
        if (registryList!=null && !registryList.isEmpty()) {
            if (ExecutorRouteStrategyEnum.SHARDING_BROADCAST == executorRouteStrategyEnum) {
                //如果是分片广播，就用分片数组中的参数选取对应的执行器地址
                if (index < group.getRegistryList().size()) {
                    address = group.getRegistryList().get(index);
                } else {
                    //如果走到这里说明上面的索引超过集合长度了，这就出错了，所以直接用默认值0号索引
                    address = group.getRegistryList().get(0);
                }
            }else {
                //走到这里说明不是分片广播，那就根据路由策略获得最终选用的执行器地址
                routeAddressResult = executorRouteStrategyEnum.getRouter().route(triggerParam, registryList);
                if (routeAddressResult.getCode() == ReturnT.SUCCESS_CODE) {
                    address = routeAddressResult.getContent();
                }
            }
        }else {
            //如果没得到地址，就赋值失败，这里还用不到这个失败结果，但是先列出来吧
            routeAddressResult = new ReturnT<String>(ReturnT.FAIL_CODE, I18nUtil.getString("jobconf_trigger_address_empty"));
        }
        //接下来就定义一个远程调用的结果变量
        ReturnT<String> triggerResult = null;
        //如果地址不为空
        if (address != null) {
            //在这里进行远程调用，这里就是最核心远程调用的方法，但是方法内部的逻辑很简单，就是用http发送调用
            //消息而已
            triggerResult = runExecutor(triggerParam, address);
        } else {
            triggerResult = new ReturnT<String>(ReturnT.FAIL_CODE, null);
        }
        //在这里拼接一下触发任务的信息，其实就是web界面的调度备注
        StringBuffer triggerMsgSb = new StringBuffer();
        triggerMsgSb.append(I18nUtil.getString("jobconf_trigger_type")).append("：").append(triggerType.getTitle());
        triggerMsgSb.append("<br>").append(I18nUtil.getString("jobconf_trigger_admin_adress")).append("：").append(IpUtil.getIp());
        triggerMsgSb.append("<br>").append(I18nUtil.getString("jobconf_trigger_exe_regtype")).append("：")
                .append( (group.getAddressType() == 0)?I18nUtil.getString("jobgroup_field_addressType_0"):I18nUtil.getString("jobgroup_field_addressType_1") );
        triggerMsgSb.append("<br>").append(I18nUtil.getString("jobconf_trigger_exe_regaddress")).append("：").append(group.getRegistryList());
        triggerMsgSb.append("<br>").append(I18nUtil.getString("jobinfo_field_executorRouteStrategy")).append("：").append(executorRouteStrategyEnum.getTitle());
        if (shardingParam != null) {
            triggerMsgSb.append("("+shardingParam+")");
        }
        triggerMsgSb.append("<br>").append(I18nUtil.getString("jobinfo_field_executorBlockStrategy")).append("：").append(blockStrategy.getTitle());
        triggerMsgSb.append("<br>").append(I18nUtil.getString("jobinfo_field_timeout")).append("：").append(jobInfo.getExecutorTimeout());
        triggerMsgSb.append("<br>").append(I18nUtil.getString("jobinfo_field_executorFailRetryCount")).append("：").append(finalFailRetryCount);
        triggerMsgSb.append("<br><br><span style=\"color:#00c0ef;\" > >>>>>>>>>>>"+ I18nUtil.getString("jobconf_trigger_run") +"<<<<<<<<<<< </span><br>")
                .append((routeAddressResult!=null&&routeAddressResult.getMsg()!=null)?routeAddressResult.getMsg()+"<br><br>":"").append(triggerResult.getMsg()!=null?triggerResult.getMsg():"");
        //设置执行器地址
        jobLog.setExecutorAddress(address);
        //设置执行定时任务的方法名称
        jobLog.setExecutorHandler(jobInfo.getExecutorHandler());
        //设置执行参数
        jobLog.setExecutorParam(jobInfo.getExecutorParam());
        //设置分片参数
        jobLog.setExecutorShardingParam(shardingParam);
        //设置失败重试次数
        jobLog.setExecutorFailRetryCount(finalFailRetryCount);
        //设置触发结果码
        jobLog.setTriggerCode(triggerResult.getCode());
        //设置触发任务信息，也就是调度备注
        jobLog.setTriggerMsg(triggerMsgSb.toString());
        //更新数据库信息
        XxlJobAdminConfig.getAdminConfig().getXxlJobLogDao().updateTriggerInfo(jobLog);
        logger.debug(">>>>>>>>>>> xxl-job trigger end, jobId:{}", jobLog.getId());
    }


    /**
     * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/7/4
     * @Description:该方法内进行远程调用
     */
    public static ReturnT<String> runExecutor(TriggerParam triggerParam, String address){
        ReturnT<String> runResult = null;
        try {
            //获取一个用于远程调用的客户端对象，一个地址就对应着一个客户端，为什么说是客户端，因为远程调用的时候，执行器
            //就成为了服务端，因为执行器要接收来自客户端的调用消息
            ExecutorBiz executorBiz = XxlJobScheduler.getExecutorBiz(address);
            //客户端获得之后，就在run方法内进行远程调用了
            runResult = executorBiz.run(triggerParam);
        } catch (Exception e) {
            logger.error(">>>>>>>>>>> xxl-job trigger error, please check if the executor[{}] is running.", address, e);
            runResult = new ReturnT<String>(ReturnT.FAIL_CODE, ThrowableUtil.toString(e));
        }
        //在这里拼接一下远程调用返回的状态码和消息
        StringBuffer runResultSB = new StringBuffer(I18nUtil.getString("jobconf_trigger_run") + "：");
        runResultSB.append("<br>address：").append(address);
        runResultSB.append("<br>code：").append(runResult.getCode());
        runResultSB.append("<br>msg：").append(runResult.getMsg());
        runResult.setMsg(runResultSB.toString());
        return runResult;
    }

}
