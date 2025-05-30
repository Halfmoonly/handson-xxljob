package com.cqfy.xxl.job.core.server;

import com.cqfy.xxl.job.core.biz.ExecutorBiz;
import com.cqfy.xxl.job.core.biz.impl.ExecutorBizImpl;
import com.cqfy.xxl.job.core.biz.model.*;
import com.cqfy.xxl.job.core.thread.ExecutorRegistryThread;
import com.cqfy.xxl.job.core.util.GsonTool;
import com.cqfy.xxl.job.core.util.ThrowableUtil;
import com.cqfy.xxl.job.core.util.XxlJobRemotingUtil;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

/**
 * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
 * @Date:2023/7/8
 * @Description:执行器这一端内嵌的netty服务器
 */
public class EmbedServer {

    private static final Logger logger = LoggerFactory.getLogger(EmbedServer.class);

    //这个是执行器接口，但是在start方法中会被接口的实现类赋值
    private ExecutorBiz executorBiz;
    //启动Netty服务器的线程，这说明内嵌服务器的启动也是异步的
    private Thread thread;


    /**
     * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/7/8
     * @Description:启动执行器的内嵌服务器
     */
    public void start(final String address, final int port, final String appname, final String accessToken) {
        //给executorBiz赋值，这个ExecutorBizImpl对象相当重要，它就是用来执行定时任务的
        executorBiz = new ExecutorBizImpl();
        //创建线程，在线程中启动Netty服务器
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                //下面都是netty的知识，学过手写netty的应该都清楚，就不再一一解释了
                EventLoopGroup bossGroup = new NioEventLoopGroup();
                EventLoopGroup workerGroup = new NioEventLoopGroup();
                //bizThreadPool线程池会传入到下面的EmbedHttpServerHandler入站处理器中
                ThreadPoolExecutor bizThreadPool = new ThreadPoolExecutor(
                        0,
                        200,
                        60L,
                        TimeUnit.SECONDS,
                        new LinkedBlockingQueue<Runnable>(2000),
                        new ThreadFactory() {
                            @Override
                            public Thread newThread(Runnable r) {
                                return new Thread(r, "xxl-job, EmbedServer bizThreadPool-" + r.hashCode());
                            }
                        },
                        new RejectedExecutionHandler() {
                            @Override
                            public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                                throw new RuntimeException("xxl-job, EmbedServer bizThreadPool is EXHAUSTED!");
                            }
                        });
                try {
                    ServerBootstrap bootstrap = new ServerBootstrap();
                    bootstrap.group(bossGroup, workerGroup)
                            .channel(NioServerSocketChannel.class)
                            .childHandler(new ChannelInitializer<SocketChannel>() {
                                @Override
                                public void initChannel(SocketChannel channel) throws Exception {
                                    channel.pipeline()
                                            //心跳检测
                                            .addLast(new IdleStateHandler(0, 0, 30 * 3, TimeUnit.SECONDS))
                                            //http的编解码器，该处理器既是出站处理器，也是入站处理器
                                            .addLast(new HttpServerCodec())
                                            //这个处理器从名字上就能看出来，是聚合消息的，当传递的http消息过大时，会被拆分开，这里添加这个处理器
                                            //就是把拆分的消息再次聚合起来，形成一个整体再向后传递
                                            //该处理器是个入站处理器
                                            .addLast(new HttpObjectAggregator(5 * 1024 * 1024))
                                            //添加入站处理器，在该处理器中执行定时任务
                                            .addLast(new EmbedHttpServerHandler(executorBiz, accessToken, bizThreadPool));
                                }
                            })
                            .childOption(ChannelOption.SO_KEEPALIVE, true);
                    //绑定端口号
                    ChannelFuture future = bootstrap.bind(port).sync();
                    logger.info(">>>>>>>>>>> xxl-job remoting server start success, nettype = {}, port = {}", EmbedServer.class, port);
                    //注册执行器到调度中心
                    startRegistry(appname, address);
                    //等待关闭
                    future.channel().closeFuture().sync();
                } catch (InterruptedException e) {
                    logger.info(">>>>>>>>>>> xxl-job remoting server stop.");
                } catch (Exception e) {
                    logger.error(">>>>>>>>>>> xxl-job remoting server error.", e);
                } finally {
                    try {
                        //优雅释放资源
                        workerGroup.shutdownGracefully();
                        bossGroup.shutdownGracefully();
                    } catch (Exception e) {
                        logger.error(e.getMessage(), e);
                    }
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/7/8
     * @Description:销毁资源的方法
     */
    public void stop() throws Exception {
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
        }
        //销毁注册执行器到调度中心的线程
        stopRegistry();
        logger.info(">>>>>>>>>>> xxl-job remoting server destroy success.");
    }


    /**
     * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/7/8
     * @Description:程序内部定义的入站处理器，这个处理器会进行定时任务方法的调用
     */
    public static class EmbedHttpServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

        private static final Logger logger = LoggerFactory.getLogger(EmbedHttpServerHandler.class);

        //很重要的对象，其实就是ExecutorBizImpl，该对象调用定时方法
        private ExecutorBiz executorBiz;
        //token令牌
        private String accessToken;
        //bizThreadPool会赋值给下面的属性
        private ThreadPoolExecutor bizThreadPool;

        /**
         * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
         * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
         * @Date:2023/7/8
         * @Description:构造方法
         */
        public EmbedHttpServerHandler(ExecutorBiz executorBiz, String accessToken, ThreadPoolExecutor bizThreadPool) {
            this.executorBiz = executorBiz;
            this.accessToken = accessToken;
            this.bizThreadPool = bizThreadPool;
        }


        /**
         * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
         * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
         * @Date:2023/7/8
         * @Description:入站方法，在该方法中，进行定时任务的调用
         */
        @Override
        protected void channelRead0(final ChannelHandlerContext ctx, FullHttpRequest msg) throws Exception {
            //获得发送过来的消息
            String requestData = msg.content().toString(CharsetUtil.UTF_8);
            //获得uri，这个uri就是调度中心访问Netty服务器时的uri，这里之所以要获得它，是因为uri中有调度中心访问服务器的具体请求
            String uri = msg.uri();
            //发送请求的方法
            HttpMethod httpMethod = msg.method();
            //在判断http连接是否还存活，也就是是否还活跃的意思
            boolean keepAlive = HttpUtil.isKeepAlive(msg);
            //从请求中获得token令牌
            String accessTokenReq = msg.headers().get(XxlJobRemotingUtil.XXL_JOB_ACCESS_TOKEN);
            //上面Netty的单线程执行器为我们解析了消息，下面的工作就该交给用户定义的工作线程来执行吧
            //否则会拖累Netty的单线程执行器处理IO事件的效率
            bizThreadPool.execute(new Runnable() {
                @Override
                public void run() {
                    //在下面的这个方法中调度中心触发的定时任务，得到返回结果
                    Object responseObj = process(httpMethod, uri, requestData, accessTokenReq);
                    //序列化
                    String responseJson = GsonTool.toJson(responseObj);
                    //把消息回复给调度中心，注意，这里的回复消息的动作，是业务线程发起的
                    //但是学完手写netty的各位都知道，真正发送消息还是由单线程执行器来完成的
                    writeResponse(ctx, keepAlive, responseJson);
                }
            });
        }



        /**
         * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
         * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
         * @Date:2023/7/8
         * @Description:该方法中完成的功能有很多，但这里我只为大家保留了执行定时任务的功能，后续会迭代完整
         */
        private Object process(HttpMethod httpMethod, String uri, String requestData, String accessTokenReq) {
            //判断是不是post方法
            if (HttpMethod.POST != httpMethod) {
                return new ReturnT<String>(ReturnT.FAIL_CODE, "invalid request, HttpMethod not support.");
            }
            //校验uri是否为空
            if (uri == null || uri.trim().length() == 0) {
                return new ReturnT<String>(ReturnT.FAIL_CODE, "invalid request, uri-mapping empty.");
            }
            //判断执行器令牌是否和调度中心令牌一样，这里也能发现，调度中心和执行器的token令牌一定要是相等的，因为判断是双向的，两边都要判断
            if (accessToken != null
                    && accessToken.trim().length() > 0
                    && !accessToken.equals(accessTokenReq)) {
                return new ReturnT<String>(ReturnT.FAIL_CODE, "The access token is wrong.");
            }
            try {
                //开始从uri中具体判断，调度中心触发的是什么任务了
                switch (uri) {
                    //这里触发的就是心跳检测，判断执行器这一端是否启动了
                    case "/beat":
                        return executorBiz.beat();
                    case "/idleBeat":
                        //这里就是判断调度中心要调度的任务是否可以顺利执行，其实就是判断该任务是否正在被
                        //执行器这一端执行或者在执行器的队列中，如果在的话，说明当前执行器比较繁忙
                        IdleBeatParam idleBeatParam = GsonTool.fromJson(requestData, IdleBeatParam.class);
                        return executorBiz.idleBeat(idleBeatParam);
                    case "/run":
                        //run就意味着是要执行定时任务
                        //把requestData转化成触发器参数对象，也就是TriggerParam对象
                        TriggerParam triggerParam = GsonTool.fromJson(requestData, TriggerParam.class);
                        //然后交给ExecutorBizImpl对象去执行定时任务
                        return executorBiz.run(triggerParam);
                        //走到这个分支就意味着要终止任务
                    case "/kill":
                        KillParam killParam = GsonTool.fromJson(requestData, KillParam.class);
                        return executorBiz.kill(killParam);
                    case "/log":
                        //远程访问执行器端日志
                        LogParam logParam = GsonTool.fromJson(requestData, LogParam.class);
                        return executorBiz.log(logParam);
                    default:
                        return new ReturnT<String>(ReturnT.FAIL_CODE, "invalid request, uri-mapping(" + uri + ") not found.");
                }
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                return new ReturnT<String>(ReturnT.FAIL_CODE, "request error:" + ThrowableUtil.toString(e));
            }
        }



        /**
         * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
         * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
         * @Date:2023/7/8
         * @Description:该方法就是把执行的定时任务的结果发送到调度中心
         */
        private void writeResponse(ChannelHandlerContext ctx, boolean keepAlive, String responseJson) {
            //设置响应结果
            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.copiedBuffer(responseJson, CharsetUtil.UTF_8));
            //设置文本类型
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html;charset=UTF-8");
            //消息的字节长度
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
            if (keepAlive) {
                //连接是存活状态
                response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            }
            //开始发送消息
            ctx.writeAndFlush(response);
        }


        /**
         * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
         * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
         * @Date:2023/7/8
         * @Description:下面三个是Netty中入站处理器的方法的回调，在手写Netty中也分别讲解了它们的回调时机，所以，就不再解释了
         */
        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            ctx.flush();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            logger.error(">>>>>>>>>>> xxl-job provider netty_http server caught exception", cause);
            ctx.close();
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof IdleStateEvent) {
                ctx.channel().close();
                logger.debug(">>>>>>>>>>> xxl-job provider netty_http server close an idle channel.");
            } else {
                super.userEventTriggered(ctx, evt);
            }
        }
    }





    /**
     * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/7/8
     * @Description:启动注册线程，然后把执行器注册到调度中心
     */
    public void startRegistry(final String appname, final String address) {
        //启动线程，注册执行器到调度中心
        ExecutorRegistryThread.getInstance().start(appname, address);
    }

    /**
     * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/7/8
     * @Description:销毁注册线程
     */
    public void stopRegistry() {
        ExecutorRegistryThread.getInstance().toStop();
    }
}
