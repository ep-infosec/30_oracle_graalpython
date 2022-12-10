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

import static com.oracle.graal.python.builtins.objects.thread.AbstractPythonLock.TIMEOUT_MAX;
import static com.oracle.graal.python.nodes.BuiltinNames.J__THREAD;
import static com.oracle.graal.python.nodes.BuiltinNames.T__THREAD;

import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.thread.PLock;
import com.oracle.graal.python.builtins.objects.thread.PRLock;
import com.oracle.graal.python.builtins.objects.thread.PThread;
import com.oracle.graal.python.builtins.objects.thread.PThreadLocal;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.argument.keywords.ExpandKeywordStarargsNode;
import com.oracle.graal.python.nodes.argument.positional.ExecutePositionalStarargsNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonThreadKillException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(defineModule = J__THREAD)
public class ThreadModuleBuiltins extends PythonBuiltins {
    private static final HiddenKey THREAD_COUNT = new HiddenKey("thread_count");

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ThreadModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        addBuiltinConstant("error", core.lookupType(PythonBuiltinClassType.RuntimeError));
        addBuiltinConstant("TIMEOUT_MAX", TIMEOUT_MAX);
        addBuiltinConstant(THREAD_COUNT, 0);
        super.initialize(core);
    }

    @Builtin(name = "_local", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PThreadLocal)
    @GenerateNodeFactory
    abstract static class ThreadLocalNode extends PythonBuiltinNode {
        @Specialization
        PThreadLocal construct(Object cls, Object[] args, PKeyword[] keywordArgs) {
            return factory().createThreadLocal(cls, args, keywordArgs);
        }
    }

    @Builtin(name = "allocate_lock", maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class AllocateLockNode extends PythonBinaryBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        PLock construct(Object self, Object unused) {
            return factory().createLock(PythonBuiltinClassType.PLock);
        }
    }

    @Builtin(name = "LockType", minNumOfPositionalArgs = 1, constructsClass = PythonBuiltinClassType.PLock)
    @GenerateNodeFactory
    abstract static class ConstructLockNode extends PythonUnaryBuiltinNode {
        @Specialization
        PLock construct(Object cls) {
            return factory().createLock(cls);
        }
    }

    @Builtin(name = "RLock", minNumOfPositionalArgs = 1, constructsClass = PythonBuiltinClassType.PRLock)
    @GenerateNodeFactory
    abstract static class ConstructRLockNode extends PythonUnaryBuiltinNode {
        @Specialization
        PRLock construct(Object cls) {
            return factory().createRLock(cls);
        }
    }

    @Builtin(name = "get_ident", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class GetCurrentThreadIdNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        public static long getId() {
            return Thread.currentThread().getId();
        }
    }

    @Builtin(name = "get_native_id", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class GetNativeIdNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        public static long getId() {
            return Thread.currentThread().getId();
        }
    }

    @Builtin(name = "_count", minNumOfPositionalArgs = 1, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class GetThreadCountNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        long getCount(PythonModule self) {
            try {
                return DynamicObjectLibrary.getUncached().getIntOrDefault(self, THREAD_COUNT, 0);
            } catch (UnexpectedResultException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }
    }

    @Builtin(name = "stack_size", minNumOfPositionalArgs = 0, maxNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetThreadStackSizeNode extends PythonUnaryBuiltinNode {
        private final ConditionProfile invalidSizeProfile = ConditionProfile.createBinaryProfile();

        @Specialization
        long getStackSize(@SuppressWarnings("unused") PNone stackSize) {
            return getContext().getPythonThreadStackSize();
        }

        private long setAndGetStackSizeInternal(long stackSize) {
            if (invalidSizeProfile.profile(stackSize < 0)) {
                throw raise(PythonBuiltinClassType.ValueError, ErrorMessages.SIZE_MUST_BE_D_OR_S, 0, "a positive value");
            }
            return getContext().getAndSetPythonsThreadStackSize(stackSize);
        }

        @Specialization
        long getStackSize(int stackSize) {
            return setAndGetStackSizeInternal(stackSize);
        }

        @Specialization
        long getStackSize(long stackSize) {
            return setAndGetStackSizeInternal(stackSize);
        }
    }

    @Builtin(name = "start_new_thread", minNumOfPositionalArgs = 3, maxNumOfPositionalArgs = 4, constructsClass = PythonBuiltinClassType.PThread)
    @GenerateNodeFactory
    abstract static class StartNewThreadNode extends PythonBuiltinNode {
        @Specialization
        @SuppressWarnings("try")
        long start(Object cls, Object callable, Object args, Object kwargs,
                        @Cached CallNode callNode,
                        @Cached ExecutePositionalStarargsNode getArgsNode,
                        @Cached ExpandKeywordStarargsNode getKwArgsNode) {
            PythonContext context = getContext();
            TruffleLanguage.Env env = context.getEnv();
            PythonModule threadModule = context.lookupBuiltinModule(T__THREAD);

            // TODO: python thread stack size != java thread stack size
            // ignore setting the stack size for the moment
            Thread thread = env.createThread(() -> {
                try (GilNode.UncachedAcquire gil = GilNode.uncachedAcquire()) {
                    // if args is an arbitrary iterable, converting it to an Object[] may run
                    // Python code
                    Object[] arguments = getArgsNode.executeWith(null, args);
                    PKeyword[] keywords = getKwArgsNode.execute(kwargs);

                    // the increment is protected by the gil
                    DynamicObjectLibrary lib = DynamicObjectLibrary.getUncached();
                    int curCount = 0;
                    try {
                        curCount = lib.getIntOrDefault(threadModule, THREAD_COUNT, 0);
                    } catch (UnexpectedResultException ure) {
                        throw CompilerDirectives.shouldNotReachHere();
                    }
                    lib.putInt(threadModule, THREAD_COUNT, curCount + 1);
                    try {
                        // n.b.: It is important to pass 'null' frame here because each thread has
                        // it's own stack and if we would pass the current frame, this would be
                        // connected as a caller which is incorrect. However, the thread-local
                        // 'topframeref' is initialized with EMPTY which will be picked up.
                        callNode.execute(null, callable, arguments, keywords);
                    } finally {
                        // the catch blocks run ofter the gil is released, so we decrement the
                        // threadcount here while still protected by the gil
                        try {
                            curCount = lib.getIntOrDefault(threadModule, THREAD_COUNT, 1);
                        } catch (UnexpectedResultException ure) {
                            throw CompilerDirectives.shouldNotReachHere();
                        }
                        lib.putInt(threadModule, THREAD_COUNT, curCount - 1);
                    }
                } catch (PythonThreadKillException e) {
                    return;
                } catch (PException e) {
                    dumpError(context, e.getUnreifiedException(), callable);
                    // TODO (cbasca): when GR-30386 is completed use the intrinsified
                    // sys.unraisablehook
                    // WriteUnraisableNode.getUncached().execute(e.getUnreifiedException(), "in
                    // thread started by", callable);
                }
            }, env.getContext(), context.getThreadGroup());

            PThread pThread = factory().createPythonThread(cls, thread);
            pThread.start();
            return pThread.getId();
        }

        @TruffleBoundary
        static void dumpError(PythonContext context, PBaseException exception, Object object) {
            PrintWriter err = new PrintWriter(context.getStandardErr());
            err.println(String.format("%s in thread started by %s", exception.toString(), object));
        }
    }

    @Builtin(name = "_set_sentinel", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class SetSentinelNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        Object setSentinel() {
            PLock sentinelLock = factory().createLock();
            PythonContext.get(this).setSentinelLockWeakref(new WeakReference<>(sentinelLock));
            return sentinelLock;
        }
    }

    @Builtin(name = "interrupt_main", parameterNames = {"signum"}, doc = "interrupt_main()\n" +
                    "\n" +
                    "Raise a KeyboardInterrupt in the main thread.\n" +
                    "A subthread can use this function to interrupt the main thread.")
    @ArgumentClinic(name = "signum", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "SIGINT")
    @GenerateNodeFactory
    abstract static class InterruptMainThreadNode extends PythonUnaryClinicBuiltinNode {
        static final int SIGINT = 2;

        @Specialization
        @SuppressWarnings("unused")
        Object getCount(@SuppressWarnings("unused") int signum) {
            // TODO: implement me
            return PNone.NONE;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ThreadModuleBuiltinsClinicProviders.InterruptMainThreadNodeClinicProviderGen.INSTANCE;
        }
    }
}
