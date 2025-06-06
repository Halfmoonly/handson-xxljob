package com.cqfy.xxl.job.core.biz.impl;

import com.cqfy.xxl.job.core.biz.ExecutorBiz;
import com.cqfy.xxl.job.core.biz.model.*;
import com.cqfy.xxl.job.core.enums.ExecutorBlockStrategyEnum;
import com.cqfy.xxl.job.core.executor.XxlJobExecutor;
import com.cqfy.xxl.job.core.glue.GlueFactory;
import com.cqfy.xxl.job.core.glue.GlueTypeEnum;
import com.cqfy.xxl.job.core.handler.IJobHandler;
import com.cqfy.xxl.job.core.handler.impl.GlueJobHandler;
import com.cqfy.xxl.job.core.log.XxlJobFileAppender;
import com.cqfy.xxl.job.core.thread.JobThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

/**
 * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
 * @Date:2023/7/8
 * @Description:该类就是在执行器段进行定时任务调用的类
 */
public class ExecutorBizImpl implements ExecutorBiz {

    private static Logger logger = LoggerFactory.getLogger(ExecutorBizImpl.class);


    /**
     * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/7/14
     * @Description:心跳检测方法
     */
    @Override
    public ReturnT<String> beat() {
        return ReturnT.SUCCESS;
    }


    /**
     * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/7/14
     * @Description:判断调度中心调度的定时任务是否在执行器对应的任务线程的队列中
     */
    @Override
    public ReturnT<String> idleBeat(IdleBeatParam idleBeatParam) {
        boolean isRunningOrHasQueue = false;
        //获取执行定时任务的线程
        JobThread jobThread = XxlJobExecutor.loadJobThread(idleBeatParam.getJobId());
        if (jobThread != null && jobThread.isRunningOrHasQueue()) {
            //如果线程不为null，并且正在工作，就把该变量置为true
            isRunningOrHasQueue = true;
        }
        //这时候就说明调度的任务还没有被执行呢，肯定在队列里面待着呢，或者是正在执行呢
        //总之，当前执行器比较繁忙
        if (isRunningOrHasQueue) {
            //所以就可以返回一个失败的状态码
            return new ReturnT<String>(ReturnT.FAIL_CODE, "job thread is running or has trigger queue.");
        }
        return ReturnT.SUCCESS;
    }

    /**
     * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/7/8
     * @Description:执行定时任务的方法，这里要再次强调一下，该方法是在用户定义的业务线程池中调用的
     */
    @Override
    public ReturnT<String> run(TriggerParam triggerParam) {
        //通过定时任务的ID从jobThreadRepository这个Map中获取一个具体的用来执行定时任务的线程
        JobThread jobThread = XxlJobExecutor.loadJobThread(triggerParam.getJobId());
        //判断该jobThread是否为空，不为空则说明该定时任务不是第一次执行了，也就意味着该线程已经分配了定时任务了，也就是这个jobHandler对象
        //如果为空，说明该定时任务是第一次执行，还没有分配jobThread
        IJobHandler jobHandler = jobThread!=null?jobThread.getHandler():null;
        //这个变量记录的是移除旧的工作线程的原因
        String removeOldReason = null;
        //得到定时任务的调度模式
        GlueTypeEnum glueTypeEnum = GlueTypeEnum.match(triggerParam.getGlueType());
        //如果为bean模式，就通过定时任务的名字，从jobHandlerRepository这个Map中获得jobHandler
        if (GlueTypeEnum.BEAN == glueTypeEnum) {
            //在这里获得定时任务对应的jobHandler对象，其实就是MethodJobHandler对象
            IJobHandler newJobHandler = XxlJobExecutor.loadJobHandler(triggerParam.getExecutorHandler());
            //这里会进行一下判断，如果上面得到的jobHandler并不为空，说明该定时任务已经执行过了，并且分配了对应的执行任务的线程
            //但是根据定时任务的名字，从jobHandlerRepository这个Map中得到封装定时任务方法的对象却和jobHandler不相同
            //说明定时任务已经改变了
            if (jobThread!=null && jobHandler != newJobHandler) {
                //走到这里就意味着定时任务已经改变了，要做出相应处理，需要把旧的线程杀死
                removeOldReason = "change jobhandler or glue type, and terminate the old job thread.";
                //执行定时任务的线程和封装定时任务方法的对象都置为null
                jobThread = null;
                jobHandler = null;
            }
            if (jobHandler == null) {
                //如果走到这里，就意味着jobHandler为null，这也就意味着上面得到的jobThread为null
                //这就说明，这次调度的定时任务是第一次执行，所以直接让jobHandler等于从jobHandlerRepository这个Map获得newJobHandler即可
                //然后，这个jobHandler会在下面创建JobThread的时候用到
                jobHandler = newJobHandler;
                if (jobHandler == null) {
                    //经过上面的赋值，
                    //走到这里如果jobHandler仍然为null，那只有一个原因，就是执行器这一端根本就没有对应的定时任务
                    //通过执行器的名字根本从jobHandlerRepository这个Map中找不到要被执行的定时任务
                    return new ReturnT<String>(ReturnT.FAIL_CODE, "job handler [" + triggerParam.getExecutorHandler() + "] not found.");
                }
            }
        } else if (GlueTypeEnum.GLUE_GROOVY == glueTypeEnum) {
            //走到这里，说明是glue模式，在线编辑代码然后执行的
            //注意，这时候运行的事glue模式，就不能再使用MethodJobHandler了反射执行定时任务了，应该使用GlueJobHandler来执行任务
            //所以下面会先判断GlueJobHandler中的gule的更新时间，和本次要执行的任务的更新时间是否相等，如果不想等说明glue的源码可能改变了，要重新
            //创建handler和对应的工作线程
            if (jobThread != null &&
                    !(jobThread.getHandler() instanceof GlueJobHandler
                            && ((GlueJobHandler) jobThread.getHandler()).getGlueUpdatetime()==triggerParam.getGlueUpdatetime() )) {
                removeOldReason = "change job source or glue type, and terminate the old job thread.";
                jobThread = null;
                jobHandler = null;
            }
            if (jobHandler == null) {
                try {//下面就可以在创建新的handler了
                    IJobHandler originJobHandler = GlueFactory.getInstance().loadNewInstance(triggerParam.getGlueSource());
                    jobHandler = new GlueJobHandler(originJobHandler, triggerParam.getGlueUpdatetime());
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                    return new ReturnT<String>(ReturnT.FAIL_CODE, e.getMessage());
                }
            }
        }
        else {
            //如果没有合适的调度模式，就返回调用失败的信息
            return new ReturnT<String>(ReturnT.FAIL_CODE, "glueType[" + triggerParam.getGlueType() + "] is not valid.");
        }
        //走到这里只是判断jobThread不为null，说明执行器端已经为该定时任务创建了工作线程了
        if (jobThread != null) {
            //得到该定时任务的阻塞策略
            ExecutorBlockStrategyEnum blockStrategy = ExecutorBlockStrategyEnum.match(triggerParam.getExecutorBlockStrategy(), null);
            if (ExecutorBlockStrategyEnum.DISCARD_LATER == blockStrategy) {
                //走到这里说明定时任务的阻塞策略为直接丢弃
                //所以接下来要判断一下执行该定时任务的线程是否正在工作，如果正在工作并且其内部的队列中有数据
                //说明该线程执行的定时任务已经被调度过几次了，但是还未执行，只能暂时缓存在工作线程的内部队列中
                if (jobThread.isRunningOrHasQueue()) {
                    //因为阻塞策略是直接丢弃，所以直接返回失败结果
                    return new ReturnT<String>(ReturnT.FAIL_CODE, "block strategy effect："+ExecutorBlockStrategyEnum.DISCARD_LATER.getTitle());
                }
                //走到这里说明得到的阻塞策略为覆盖，覆盖的意思就是旧的任务不执行了，直接执行这个新的定时任务
            } else if (ExecutorBlockStrategyEnum.COVER_EARLY == blockStrategy) {
                if (jobThread.isRunningOrHasQueue()) {
                    removeOldReason = "block strategy effect：" + ExecutorBlockStrategyEnum.COVER_EARLY.getTitle();
                    //所以这里把工作线程的引用置为null，这样下面就可以创建一个新的工作线程，然后缓存到Map中
                    //新的工作线程就是直接执行新的定时任务了，默认的阻塞策略就是串行，都放到工作线程内部的队列中，等待被执行
                    jobThread = null;
                }
            } else {
                //这里源码中还未实现，直接空着即可
            }
        }
        if (jobThread == null) {
            //走到这里意味着定时任务是第一次执行，还没有创建对应的执行定时任务的线程，所以，就在这里把对应的线程创建出来，
            //并且缓存到jobThreadRepository这个Map中
            //在这里就用到了上面赋值过的jobHandler
            jobThread = XxlJobExecutor.registJobThread(triggerParam.getJobId(), jobHandler, removeOldReason);
        }
        //如果走到这里，不管上面是什么情况吧，总之jobThread肯定存在了，所以直接把要调度的任务放到这个线程内部的队列中
        //等待线程去调用，返回一个结果
        ReturnT<String> pushResult = jobThread.pushTriggerQueue(triggerParam);
        return pushResult;
    }


    //终止任务的方法
    @Override
    public ReturnT<String> kill(KillParam killParam) {
        //根据任务ID获取到对应的执行任务的线程
        JobThread jobThread = XxlJobExecutor.loadJobThread(killParam.getJobId());
        if (jobThread != null) {
            //从Map中移除该线程，同时也终止该线程
            XxlJobExecutor.removeJobThread(killParam.getJobId(), "scheduling center kill job.");
            return ReturnT.SUCCESS;
        }
        //返回成功结果
        return new ReturnT<String>(ReturnT.SUCCESS_CODE, "job thread already killed.");
    }

    /**
     * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/7/17
     * @Description:调度中心远程查询执行器端日志的方法
     */
    @Override
    public ReturnT<LogResult> log(LogParam logParam) {
        //根据定时任务id和触发时间创建文件名
        String logFileName = XxlJobFileAppender.makeLogFileName(new Date(logParam.getLogDateTim()), logParam.getLogId());
        //开始从日志文件中读取日志
        LogResult logResult = XxlJobFileAppender.readLog(logFileName, logParam.getFromLineNum());
        //返回结果
        return new ReturnT<LogResult>(logResult);
    }
}
