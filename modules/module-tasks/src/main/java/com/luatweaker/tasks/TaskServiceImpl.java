package com.luatweaker.tasks;
import com.luatweaker.api.task.ITaskService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import java.util.Comparator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

public class TaskServiceImpl implements ITaskService {
    private static final int MAX_QUEUED_TASKS = 10_000;
    private static final int MAX_TASKS_PER_TICK = 1_000;
    private static final long SLOW_TASK_NANOS = 25_000_000L;
    private final AtomicLong droppedTasks = new AtomicLong();
    private final AtomicLong slowTasks = new AtomicLong();
    private volatile int lastTickExecuted;
    private record DelayedTask(long triggerTimeMs, ILuaValue task, ILuaValue[] args) {}
    private final PriorityBlockingQueue<DelayedTask> delayedTasks = new PriorityBlockingQueue<>(11, Comparator.comparingLong(DelayedTask::triggerTimeMs));
    private final ConcurrentLinkedQueue<DelayedTask> deferredTasks = new ConcurrentLinkedQueue<>();
    @Override public void spawn(@NotNull ILuaEngine e,@NotNull ILuaValue t,ILuaValue[] a){if(t.isFunction())enqueue(deferredTasks,new DelayedTask(0,t,a));}
    @Override public void delay(@NotNull ILuaEngine e,double s,@NotNull ILuaValue t,ILuaValue[] a){if(t.isFunction())enqueue(delayedTasks,new DelayedTask(System.currentTimeMillis()+Math.max(0L,(long)(s*1000.0)),t,a));}
    @Override public void defer(@NotNull ILuaEngine e,@NotNull ILuaValue t,ILuaValue[] a){if(t.isFunction())enqueue(deferredTasks,new DelayedTask(0,t,a));}
    @Override public void cancel(@NotNull ILuaValue t){deferredTasks.removeIf(x->x.task().equals(t));delayedTasks.removeIf(x->x.task().equals(t));}
    @Override public void tick(@NotNull ILuaEngine e){int n=0;DelayedTask t;while(n<MAX_TASKS_PER_TICK&&(t=deferredTasks.poll())!=null){execute(e,t,"deferred");n++;}long now=System.currentTimeMillis();while(n<MAX_TASKS_PER_TICK&&(t=delayedTasks.peek())!=null&&now>=t.triggerTimeMs()){delayedTasks.poll();execute(e,t,"delayed");n++;}lastTickExecuted=n;}
    public int getQueuedTaskCount(){return deferredTasks.size()+delayedTasks.size();}
    public long getDroppedTaskCount(){return droppedTasks.get();}
    public long getSlowTaskCount(){return slowTasks.get();}
    public int getLastTickExecuted(){return lastTickExecuted;}
    private void enqueue(Queue<DelayedTask> q,DelayedTask t){if(getQueuedTaskCount()>=MAX_QUEUED_TASKS){droppedTasks.incrementAndGet();com.luatweaker.api.log.LuaTweakerLog.get().warn(com.luatweaker.api.log.LogStage.RUNTIME_ERROR,"Lua task queue limit reached; dropping new task");return;}q.add(t);}
    private void invokeAsCoroutine(ILuaEngine e,DelayedTask t){ILuaTable globals=e.getGlobalEnvironment();ILuaValue taskValue=globals.rawget("task");if(taskValue!=null&&taskValue.isTable()){ILuaValue runner=taskValue.asTable().rawget("_run_deferred");if(runner!=null&&runner.isFunction()){ILuaTable argsTable=e.createTable();ILuaValue[] taskArgs=t.args()==null?new ILuaValue[0]:t.args();for(int i=0;i<taskArgs.length;i++){argsTable.rawset(i+1,taskArgs[i]);}e.callFunction(runner,t.task(),argsTable);return;}}e.callFunction(t.task(),t.args()==null?new ILuaValue[0]:t.args());}
    private void execute(ILuaEngine e,DelayedTask t,String kind){long start=System.nanoTime();try{invokeAsCoroutine(e,t);}catch(RuntimeException x){com.luatweaker.api.log.LuaTweakerLog.get().error(com.luatweaker.api.log.LogStage.RUNTIME_ERROR,"Lua "+kind+" task failed: "+(x.getMessage()==null?x.getClass().getSimpleName():x.getMessage()));}finally{long d=System.nanoTime()-start;if(d>SLOW_TASK_NANOS){slowTasks.incrementAndGet();com.luatweaker.api.log.LuaTweakerLog.get().warn(com.luatweaker.api.log.LogStage.RUNTIME_ERROR,"Slow Lua "+kind+" task: "+d/1_000_000L+"ms");}}}
}
