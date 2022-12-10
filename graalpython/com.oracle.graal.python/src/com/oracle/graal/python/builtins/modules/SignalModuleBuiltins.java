/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.nodes.BuiltinNames.T__SIGNAL;
import static com.oracle.graal.python.util.TimeUtils.SEC_TO_US;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyCallableCheckNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyTimeFromObjectNode;
import com.oracle.graal.python.lib.PyTimeFromObjectNode.RoundType;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.runtime.AsyncHandler;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.object.HiddenKey;

import sun.misc.Signal;
import sun.misc.SignalHandler;

@CoreFunctions(defineModule = "_signal")
public class SignalModuleBuiltins extends PythonBuiltins {
    private static final ConcurrentHashMap<Integer, Object> signalHandlers = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, SignalHandler> defaultSignalHandlers = new ConcurrentHashMap<>();

    private static final HiddenKey signalModuleDataKey = new HiddenKey("signalModuleData");
    private final ModuleData moduleData = new ModuleData();

    private static final int ITIMER_REAL = 0;
    private static final int ITIMER_VIRTUAL = 1;
    private static final int ITIMER_PROF = 2;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SignalModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
        addBuiltinConstant("SIG_DFL", Signals.SIG_DFL);
        addBuiltinConstant("SIG_IGN", Signals.SIG_IGN);
        addBuiltinConstant("ITIMER_REAL", ITIMER_REAL);
        addBuiltinConstant("ITIMER_VIRTUAL", ITIMER_VIRTUAL);
        addBuiltinConstant("ITIMER_PROF", ITIMER_PROF);
        for (int i = 0; i < Signals.signalNames.length; i++) {
            String name = Signals.signalNames[i];
            if (name != null) {
                addBuiltinConstant("SIG" + name, i);
            }
        }
    }

    @Override
    public void postInitialize(Python3Core core) {
        super.postInitialize(core);

        PythonModule signalModule = core.lookupBuiltinModule(T__SIGNAL);
        signalModule.setAttribute(signalModuleDataKey, moduleData);

        core.getContext().registerAsyncAction(() -> {
            SignalTriggerAction poll = moduleData.signalQueue.poll();
            try {
                while (poll == null) {
                    moduleData.signalSema.acquire();
                    poll = moduleData.signalQueue.poll();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return poll;
        });
    }

    private static class SignalTriggerAction extends AsyncHandler.AsyncPythonAction {
        private final Object callableObject;
        private final int signum;

        SignalTriggerAction(Object callable, int signum) {
            this.callableObject = callable;
            this.signum = signum;
        }

        @Override
        public Object callable() {
            return callableObject;
        }

        @Override
        public Object[] arguments() {
            return new Object[]{signum, null};
        }

        @Override
        public int frameIndex() {
            return 1;
        }
    }

    @Builtin(name = "alarm", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class AlarmNode extends PythonUnaryBuiltinNode {
        @Specialization
        int alarm(long seconds) {
            Signals.scheduleAlarm(seconds);
            return 0;
        }

        @Specialization(rewriteOn = OverflowException.class)
        int alarm(PInt seconds) throws OverflowException {
            Signals.scheduleAlarm(seconds.longValueExact());
            return 0;
        }

        @Specialization
        int alarmOvf(PInt seconds) {
            try {
                Signals.scheduleAlarm(seconds.longValueExact());
                return 0;
            } catch (OverflowException e) {
                throw raise(PythonErrorType.OverflowError, ErrorMessages.PYTHON_INT_TOO_LARGE_TO_CONV_TO, "C long");
            }
        }
    }

    @TruffleBoundary
    private static Object handlerToPython(SignalHandler handler, int signum) {
        if (handler == sun.misc.SignalHandler.SIG_DFL) {
            return Signals.SIG_DFL;
        } else if (handler == sun.misc.SignalHandler.SIG_IGN) {
            return Signals.SIG_IGN;
        } else if (handler instanceof Signals.PythonSignalHandler) {
            return signalHandlers.getOrDefault(signum, PNone.NONE);
        } else {
            // Save default JVM handlers to be restored later
            defaultSignalHandlers.put(signum, handler);
            return Signals.SIG_DFL;
        }
    }

    @Builtin(name = "getsignal", minNumOfPositionalArgs = 1, parameterNames = {"signalnum"})
    @ArgumentClinic(name = "signalnum", conversion = ArgumentClinic.ClinicConversion.Index)
    @GenerateNodeFactory
    abstract static class GetSignalNode extends PythonUnaryClinicBuiltinNode {
        @Specialization
        @TruffleBoundary
        static Object getsignal(int signum) {
            return handlerToPython(Signals.getCurrentSignalHandler(signum), signum);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SignalModuleBuiltinsClinicProviders.GetSignalNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "default_int_handler", minNumOfPositionalArgs = 0, takesVarArgs = true, takesVarKeywordArgs = false)
    @GenerateNodeFactory
    abstract static class DefaultIntHandlerNode extends PythonBuiltinNode {
        @Specialization
        Object defaultIntHandler(@SuppressWarnings("unused") Object[] args) {
            // TODO should be implemented properly.
            throw raise(PythonErrorType.KeyboardInterrupt);
        }
    }

    @Builtin(name = "signal", minNumOfPositionalArgs = 3, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class SignalNode extends PythonTernaryBuiltinNode {

        @Specialization(guards = "!callableCheck.execute(idNum)", limit = "1")
        Object signalId(VirtualFrame frame, @SuppressWarnings("unused") PythonModule self, Object signal, Object idNum,
                        @SuppressWarnings("unused") @Shared("callableCheck") @Cached PyCallableCheckNode callableCheck,
                        @Shared("asSize") @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached CastToJavaIntExactNode cast) {
            // Note: CPython checks if id is the same reference as SIG_IGN/SIG_DFL constants, which
            // are instances of Handlers enum
            // The -1 fallback will be correctly reported as an error later on
            int id;
            try {
                id = cast.execute(idNum);
            } catch (CannotCastException | PException e) {
                id = -1;
            }
            return signal(asSizeNode.executeExact(frame, signal), id);
        }

        @TruffleBoundary
        private Object signal(int signum, int id) {
            SignalHandler oldHandler;
            try {
                if (id == Signals.SIG_DFL && defaultSignalHandlers.containsKey(signum)) {
                    oldHandler = Signals.setSignalHandler(signum, defaultSignalHandlers.get(signum));
                } else {
                    oldHandler = Signals.setSignalHandler(signum, id);
                }
            } catch (IllegalArgumentException e) {
                throw raise(PythonErrorType.TypeError, ErrorMessages.SIGNAL_MUST_BE_SIGIGN_SIGDFL_OR_CALLABLE_OBJ);
            }
            Object result = handlerToPython(oldHandler, signum);
            signalHandlers.remove(signum);
            return result;
        }

        @Specialization(guards = "callableCheck.execute(handler)", limit = "1")
        Object signalHandler(VirtualFrame frame, PythonModule self, Object signal, Object handler,
                        @SuppressWarnings("unused") @Shared("callableCheck") @Cached PyCallableCheckNode callableCheck,
                        @Shared("asSize") @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached ReadAttributeFromObjectNode readModuleDataNode) {
            return signal(self, asSizeNode.executeExact(frame, signal), handler, readModuleDataNode);
        }

        @TruffleBoundary
        private Object signal(PythonModule self, int signum, Object handler, ReadAttributeFromObjectNode readModuleDataNode) {
            ModuleData moduleData = getModuleData(self, readModuleDataNode);
            SignalHandler oldHandler;
            SignalTriggerAction signalTrigger = new SignalTriggerAction(handler, signum);
            try {
                oldHandler = Signals.setSignalHandler(signum, () -> {
                    moduleData.signalQueue.add(signalTrigger);
                    moduleData.signalSema.release();
                });
            } catch (IllegalArgumentException e) {
                throw raise(PythonErrorType.ValueError, e);
            }
            Object result = handlerToPython(oldHandler, signum);
            signalHandlers.put(signum, handler);
            return result;
        }
    }

    @Builtin(name = "set_wakeup_fd", minNumOfPositionalArgs = 1, parameterNames = {"", "warn_on_full_buffer"})
    @GenerateNodeFactory
    abstract static class SetWakeupFdNode extends PythonBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        static int doGeneric(Object fd, Object warnOnFullBuffer) {
            // TODO: implement
            return -1;
        }
    }

    @Builtin(name = "raise_signal", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"signalnum"})
    @ArgumentClinic(name = "signalnum", conversion = ArgumentClinic.ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class RaiseSignalNode extends PythonUnaryClinicBuiltinNode {
        @Specialization
        @TruffleBoundary
        static PNone doInt(int signum) {
            Signal.raise(new sun.misc.Signal(Signals.signalNumberToName(signum)));
            return PNone.NONE;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SignalModuleBuiltinsClinicProviders.RaiseSignalNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "setitimer", minNumOfPositionalArgs = 2, declaresExplicitSelf = true, parameterNames = {"$self", "which", "seconds", "interval"})
    @ArgumentClinic(name = "which", conversion = ArgumentClinic.ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class SetitimerNode extends PythonQuaternaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SignalModuleBuiltinsClinicProviders.SetitimerNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        Object doIt(VirtualFrame frame, PythonModule self, int which, Object seconds, Object interval,
                        @Cached ReadAttributeFromObjectNode readModuleDataNode,
                        @Cached PyTimeFromObjectNode timeFromObjectNode) {
            ModuleData moduleData = getModuleData(self, readModuleDataNode);
            long usDelay = toMicroseconds(frame, seconds, timeFromObjectNode);
            long usInterval = toMicroseconds(frame, interval, timeFromObjectNode);
            if (which != ITIMER_REAL) {
                throw raiseOSError(frame, OSErrorEnum.EINVAL);
            }
            PTuple resultTuple = GetitimerNode.createResultTuple(factory(), moduleData);
            setitimer(moduleData, usDelay, usInterval);
            return resultTuple;
        }

        private static long toMicroseconds(VirtualFrame frame, Object obj, PyTimeFromObjectNode timeFromObjectNode) {
            if (obj == PNone.NO_VALUE) {
                return 0;
            }
            return timeFromObjectNode.execute(frame, obj, RoundType.CEILING, SEC_TO_US);
        }

        @TruffleBoundary
        private void setitimer(ModuleData moduleData, long usDelay, long usInterval) {
            if (moduleData.itimerFuture != null) {
                moduleData.itimerFuture.cancel(false);
                moduleData.itimerFuture = null;
                moduleData.itimerInterval = 0;
            }
            if (usDelay == 0) {
                return;
            }
            moduleData.itimerInterval = usInterval;
            Runnable r = () -> Signals.raiseSignal("ALRM");
            ScheduledExecutorService itimerService = getItimerService(moduleData);
            if (usInterval == 0) {
                moduleData.itimerFuture = itimerService.schedule(r, usDelay, TimeUnit.MICROSECONDS);
            } else {
                moduleData.itimerFuture = itimerService.scheduleAtFixedRate(r, usDelay, usInterval, TimeUnit.MICROSECONDS);
            }
        }

        @TruffleBoundary
        private ScheduledExecutorService getItimerService(ModuleData moduleData) {
            if (moduleData.itimerService == null) {
                ScheduledExecutorService itimerService = Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread t = Executors.defaultThreadFactory().newThread(runnable);
                    t.setDaemon(true);
                    return t;
                });
                moduleData.itimerService = itimerService;
                getContext().registerAtexitHook(ctx -> itimerService.shutdown());
            }
            return moduleData.itimerService;
        }
    }

    @Builtin(name = "getitimer", minNumOfPositionalArgs = 1, declaresExplicitSelf = true, parameterNames = {"$self", "which"})
    @ArgumentClinic(name = "which", conversion = ArgumentClinic.ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class GetitimerNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SignalModuleBuiltinsClinicProviders.GetitimerNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        Object doIt(VirtualFrame frame, PythonModule self, int which,
                        @Cached ReadAttributeFromObjectNode readModuleDataNode) {
            ModuleData moduleData = getModuleData(self, readModuleDataNode);
            if (which != ITIMER_REAL) {
                throw raiseOSError(frame, OSErrorEnum.EINVAL);
            }
            return createResultTuple(factory(), moduleData);
        }

        static PTuple createResultTuple(PythonObjectFactory factory, ModuleData moduleData) {
            long oldInterval = moduleData.itimerInterval;
            long oldDelay = getOldDelay(moduleData);
            return factory.createTuple(new Object[]{oldDelay / (double) SEC_TO_US, oldInterval / (double) SEC_TO_US});
        }

        @TruffleBoundary
        static long getOldDelay(ModuleData moduleData) {
            if (moduleData.itimerFuture == null) {
                return 0;
            }
            long delay = moduleData.itimerFuture.getDelay(TimeUnit.MICROSECONDS);
            if (delay < 0) {
                return 0;
            }
            return delay;
        }
    }

    private static ModuleData getModuleData(PythonModule self, ReadAttributeFromObjectNode readNode) {
        Object obj = readNode.execute(self, signalModuleDataKey);
        if (obj instanceof ModuleData) {
            return (ModuleData) obj;
        } else {
            throw new IllegalStateException("the signal module was not initialized properly!");
        }
    }

    private static class ModuleData {
        final ConcurrentLinkedDeque<SignalTriggerAction> signalQueue = new ConcurrentLinkedDeque<>();
        final Semaphore signalSema = new Semaphore(0);
        ScheduledExecutorService itimerService;
        ScheduledFuture<?> itimerFuture;
        long itimerInterval;
    }

}

// Checkstyle: stop
/*
 * (tfel): This class is supposed to go away to be replaced with a yet to be designed Truffle API
 * for signals
 */
final class Signals {
    static final int SIG_DFL = 0;
    static final int SIG_IGN = 1;
    private static final int SIGMAX = 31;
    static final String[] signalNames = new String[SIGMAX + 1];

    static {
        for (String signal : new String[]{"ABRT", "ALRM", "BUS", "FPE", "HUP", "ILL", "INFO", "INT", "KILL", "LOST",
                        "PIPE", "PWR", "QUIT", "SEGV", "SYS", "TERM", "TRAP", "TSTP", "TTIN", "TTOUT", "USR1", "USR2",
                        "VTALRM", "WINCH"}) {
            try {
                int number = new sun.misc.Signal(signal).getNumber();
                if (number > SIGMAX) {
                    continue;
                }
                signalNames[number] = signal;
            } catch (IllegalArgumentException e) {
            }
        }
    }

    private static class Alarm implements Runnable {
        private final long seconds;

        Alarm(long seconds) {
            this.seconds = seconds;
        }

        @Override
        public void run() {
            long t0 = System.currentTimeMillis();
            while ((System.currentTimeMillis() - t0) < seconds * 1000) {
                try {
                    Thread.sleep(seconds * 1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            sun.misc.Signal.raise(new sun.misc.Signal("ALRM"));
        }
    }

    @TruffleBoundary
    synchronized static void scheduleAlarm(long seconds) {
        new Thread(new Alarm(seconds)).start();
    }

    @TruffleBoundary
    static void raiseSignal(String name) {
        sun.misc.Signal.raise(new sun.misc.Signal(name));
    }

    static class PythonSignalHandler implements sun.misc.SignalHandler {
        private final Runnable handler;

        public PythonSignalHandler(Runnable handler) {
            this.handler = handler;
        }

        @Override
        public void handle(sun.misc.Signal arg0) {
            handler.run();
        }
    }

    static String signalNumberToName(int signum) {
        return signum > SIGMAX ? "INVALID SIGNAL" : signalNames[signum];
    }

    @TruffleBoundary
    synchronized static SignalHandler setSignalHandler(int signum, Runnable handler) throws IllegalArgumentException {
        return setSignalHandler(signum, new PythonSignalHandler(handler));
    }

    @TruffleBoundary
    synchronized static SignalHandler setSignalHandler(int signum, SignalHandler handler) throws IllegalArgumentException {
        return sun.misc.Signal.handle(new sun.misc.Signal(signalNumberToName(signum)), handler);
    }

    @TruffleBoundary
    synchronized static SignalHandler setSignalHandler(int signum, int handler) throws IllegalArgumentException {
        sun.misc.SignalHandler h;
        if (handler == SIG_DFL) {
            h = sun.misc.SignalHandler.SIG_DFL;
        } else if (handler == SIG_IGN) {
            h = sun.misc.SignalHandler.SIG_IGN;
        } else {
            throw new IllegalArgumentException();
        }
        return sun.misc.Signal.handle(new sun.misc.Signal(signalNumberToName(signum)), h);
    }

    @TruffleBoundary
    synchronized static SignalHandler getCurrentSignalHandler(int signum) {
        // To check what the current signal handler, we install default to get the current one
        // and immediately replace it again.
        sun.misc.SignalHandler oldH;
        try {
            oldH = sun.misc.Signal.handle(new sun.misc.Signal(signalNumberToName(signum)), sun.misc.SignalHandler.SIG_DFL);
        } catch (IllegalArgumentException e) {
            return sun.misc.SignalHandler.SIG_DFL;
        }
        try {
            sun.misc.Signal.handle(new sun.misc.Signal(signalNumberToName(signum)), oldH);
        } catch (IllegalArgumentException e) {
            return sun.misc.SignalHandler.SIG_DFL;
        }
        return oldH;
    }
}
