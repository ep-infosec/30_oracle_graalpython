/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.builtins.objects.function;

import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.frame.PFrame.Reference;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;

//@formatter:off
/**
 * The layout of an argument array for a Python frame.
 *
 *                                         +-------------------+
 * INDEX_VARIABLE_ARGUMENTS             -> | Object[]          |  This slot is also used to pass parent frame reference in bytecode OSR compilation.
 *                                         +-------------------+
 * INDEX_KEYWORD_ARGUMENTS              -> | PKeyword[]        |
 *                                         +-------------------+
 * INDEX_GENERATOR_FRAME                -> | MaterializedFrame |
 *                                         +-------------------+
 * SPECIAL_ARGUMENT                     -> | Object            |
 *                                         +-------------------+
 * INDEX_GLOBALS_ARGUMENT               -> | PythonObject      |
 *                                         +-------------------+
 * INDEX_CLOSURE                        -> | PCell[]           |
 *                                         +-------------------+
 * INDEX_CALLER_FRAME_INFO              -> | PFrame.Reference  |
 *                                         +-------------------+
 * INDEX_CURRENT_FRAME_INFO             -> | PFrame.Reference  |
 *                                         +-------------------+
 * INDEX_CURRENT_EXCEPTION              -> | PException        |
 *                                         +-------------------+
 * USER_ARGUMENTS                       -> | arg_0             |
 *                                         | arg_1             |
 *                                         | ...               |
 *                                         | arg_(nArgs-1)     |
 *                                         +-------------------+
 *
 * The layout of a generator frame (stored in INDEX_GENERATOR_FRAME in the figure above)
 * is different in on place:
 *
 * MaterializedFrame
 *       |
 *       |                              ....
 *       |                    |                      |
 *       |                    +----------------------+
 * INDEX_CALLER_FRAME_INFO -> | PDict (locals)       |
 *                            +----------------------+
 *                            |         ....         |
 */
//@formatter:on
public final class PArguments {
    private static final FrameDescriptor EMTPY_FD = new FrameDescriptor();

    private static final int INDEX_VARIABLE_ARGUMENTS = 0;
    private static final int INDEX_KEYWORD_ARGUMENTS = 1;
    private static final int INDEX_GENERATOR_FRAME = 2;
    private static final int INDEX_SPECIAL_ARGUMENT = 3;
    private static final int INDEX_GLOBALS_ARGUMENT = 4;
    private static final int INDEX_CLOSURE = 5;
    private static final int INDEX_CALLER_FRAME_INFO = 6;
    private static final int INDEX_CURRENT_FRAME_INFO = 7;
    private static final int INDEX_CURRENT_EXCEPTION = 8;
    public static final int USER_ARGUMENTS_OFFSET = 9;

    public static boolean isPythonFrame(Frame frame) {
        return frame != null && isPythonFrame(frame.getArguments());
    }

    public static boolean isPythonFrame(Object[] frameArgs) {
        return frameArgs.length >= USER_ARGUMENTS_OFFSET && frameArgs[INDEX_KEYWORD_ARGUMENTS] instanceof PKeyword[];
    }

    public static boolean isGeneratorFrame(Frame frame) {
        return frame != null && isGeneratorFrame(frame.getArguments());
    }

    public static boolean isGeneratorFrame(Object[] frameArgs) {
        // a generator frame never has a frame info
        return frameArgs.length >= USER_ARGUMENTS_OFFSET && frameArgs[INDEX_CALLER_FRAME_INFO] instanceof PDict;
    }

    public static Object[] withGlobals(PythonObject globals) {
        Object[] arguments = create();
        setGlobals(arguments, globals);
        return arguments;
    }

    public static Object[] create() {
        return create(0);
    }

    public static Object[] create(int userArgumentLength) {
        Object[] initialArguments = new Object[USER_ARGUMENTS_OFFSET + userArgumentLength];
        initialArguments[INDEX_VARIABLE_ARGUMENTS] = PythonUtils.EMPTY_OBJECT_ARRAY;
        initialArguments[INDEX_KEYWORD_ARGUMENTS] = PKeyword.EMPTY_KEYWORDS;
        return initialArguments;
    }

    public static void setVariableArguments(Object[] arguments, Object... variableArguments) {
        arguments[INDEX_VARIABLE_ARGUMENTS] = variableArguments;
    }

    public static Object[] getVariableArguments(Frame frame) {
        return getVariableArguments(frame.getArguments());
    }

    public static Object[] getVariableArguments(Object[] frame) {
        return (Object[]) frame[INDEX_VARIABLE_ARGUMENTS];
    }

    public static void setKeywordArguments(Object[] arguments, PKeyword[] keywordArguments) {
        arguments[INDEX_KEYWORD_ARGUMENTS] = keywordArguments;
    }

    public static PKeyword[] getKeywordArguments(Frame frame) {
        return getKeywordArguments(frame.getArguments());
    }

    public static PKeyword[] getKeywordArguments(Object[] frame) {
        return (PKeyword[]) frame[INDEX_KEYWORD_ARGUMENTS];
    }

    /**
     * Special arguments used to communicate various things to the callee. It is important than any
     * usage be documented and ensured that they cannot overlap.
     *
     * @see #getSpecialArgument(Frame)
     */
    public static void setSpecialArgument(Object[] arguments, Object value) {
        arguments[INDEX_SPECIAL_ARGUMENT] = value;
    }

    /**
     * The special argument is used for various purposes, none of which can occur at the same time:
     * <ul>
     * <li>The value sent to a generator via <code>send</code></li>
     * <li>An exception thrown through a generator via <code>throw</code></li>
     * <li>The custom locals in a module or class scope when called through <code>exec</code> or
     * <code>__build_class__</code></li>
     * </ul>
     */
    public static Object getSpecialArgument(Frame frame) {
        return getSpecialArgument(frame.getArguments());
    }

    /**
     * @see #getSpecialArgument(Frame)
     */
    public static Object getSpecialArgument(Object[] arguments) {
        return arguments[INDEX_SPECIAL_ARGUMENT];
    }

    public static void setGlobals(Object[] arguments, PythonObject globals) {
        arguments[INDEX_GLOBALS_ARGUMENT] = globals;
    }

    public static PythonObject getGlobals(Object[] arguments) {
        return (PythonObject) arguments[INDEX_GLOBALS_ARGUMENT];
    }

    public static PythonObject getGlobals(Frame frame) {
        return getGlobals(frame.getArguments());
    }

    public static PythonObject getGlobalsSafe(Frame frame) {
        if (frame.getArguments()[INDEX_GLOBALS_ARGUMENT] instanceof PythonObject) {
            return getGlobals(frame);
        } else {
            return null;
        }
    }

    public static PFrame.Reference getCallerFrameInfo(Frame frame) {
        return getCallerFrameInfo(frame.getArguments());
    }

    public static PFrame.Reference getCallerFrameInfo(Object[] args) {
        return (PFrame.Reference) args[INDEX_CALLER_FRAME_INFO];
    }

    public static void setCallerFrameInfo(Object[] arguments, PFrame.Reference info) {
        arguments[INDEX_CALLER_FRAME_INFO] = info;
    }

    /**
     * This is only safe before the frame has started executing.
     */
    public static Object getCustomLocals(Frame frame) {
        return frame.getArguments()[INDEX_CURRENT_FRAME_INFO];
    }

    public static void setCustomLocals(Object[] args, Object locals) {
        assert args[INDEX_CURRENT_FRAME_INFO] == null : "cannot set custom locals if the frame is already available";
        args[INDEX_CURRENT_FRAME_INFO] = locals;
    }

    public static PFrame.Reference getCurrentFrameInfo(Frame frame) {
        return getCurrentFrameInfo(frame.getArguments());
    }

    public static PFrame.Reference getCurrentFrameInfo(Object[] args) {
        return (PFrame.Reference) args[INDEX_CURRENT_FRAME_INFO];
    }

    public static void setCurrentFrameInfo(Frame frame, PFrame.Reference info) {
        setCurrentFrameInfo(frame.getArguments(), info);
    }

    public static void setCurrentFrameInfo(Object[] arguments, PFrame.Reference info) {
        arguments[INDEX_CURRENT_FRAME_INFO] = info;
    }

    public static PException getException(Object[] arguments) {
        return (PException) getExceptionUnchecked(arguments);
    }

    public static PException getException(Frame frame) {
        return (PException) getExceptionUnchecked(frame.getArguments());
    }

    public static Object getExceptionUnchecked(Object[] arguments) {
        return arguments[INDEX_CURRENT_EXCEPTION];
    }

    public static void setException(Frame frame, PException exc) {
        setException(frame.getArguments(), exc);
    }

    public static void setException(Object[] arguments, PException exc) {
        setExceptionUnchecked(arguments, exc);
    }

    public static void setExceptionUnchecked(Object[] arguments, Object exc) {
        arguments[INDEX_CURRENT_EXCEPTION] = exc;
    }

    public static void setClosure(Object[] arguments, PCell[] closure) {
        arguments[INDEX_CLOSURE] = closure;
    }

    public static PCell[] getClosure(Object[] arguments) {
        return (PCell[]) arguments[INDEX_CLOSURE];
    }

    /*
     * We repurpose the varargs slot for storing the OSR frame. In the bytecode interpreter, varargs
     * should only be read once at the beginning of execute which is before OSR.
     */
    public static void setOSRFrame(Object[] arguments, VirtualFrame parentFrame) {
        CompilerAsserts.neverPartOfCompilation();
        arguments[INDEX_VARIABLE_ARGUMENTS] = parentFrame;
    }

    public static Frame getOSRFrame(Object[] arguments) {
        return (Frame) arguments[INDEX_VARIABLE_ARGUMENTS];
    }

    public static PCell[] getClosure(Frame frame) {
        return getClosure(frame.getArguments());
    }

    public static void setArgument(Object[] arguments, int index, Object value) {
        arguments[USER_ARGUMENTS_OFFSET + index] = value;
    }

    public static Object getArgument(Object[] arguments, int index) {
        int argIdx = USER_ARGUMENTS_OFFSET + index;
        if (argIdx < arguments.length) {
            return arguments[argIdx];
        } else {
            return null;
        }
    }

    public static Object getArgument(Frame frame, int index) {
        return getArgument(frame.getArguments(), index);
    }

    public static int getUserArgumentLength(VirtualFrame frame) {
        return frame.getArguments().length - USER_ARGUMENTS_OFFSET;
    }

    public static int getUserArgumentLength(Object[] arguments) {
        return arguments.length - USER_ARGUMENTS_OFFSET;
    }

    public static MaterializedFrame getGeneratorFrame(Object[] arguments) {
        return (MaterializedFrame) arguments[INDEX_GENERATOR_FRAME];
    }

    public static MaterializedFrame getGeneratorFrame(Frame frame) {
        return getGeneratorFrame(frame.getArguments());
    }

    public static MaterializedFrame getGeneratorFrameSafe(Frame frame) {
        return getGeneratorFrameSafe(frame.getArguments());
    }

    public static MaterializedFrame getGeneratorFrameSafe(Object[] arguments) {
        if (arguments[INDEX_GENERATOR_FRAME] instanceof MaterializedFrame) {
            return getGeneratorFrame(arguments);
        } else {
            return null;
        }
    }

    public static void setGeneratorFrame(Object[] arguments, MaterializedFrame generatorFrame) {
        arguments[INDEX_GENERATOR_FRAME] = generatorFrame;
    }

    /**
     * This should be used only in GeneratorFunctionRootNode, later the slot is overwritten with
     * generator frame
     */
    public static PFunction getGeneratorFunction(Object[] arguments) {
        return (PFunction) arguments[INDEX_GENERATOR_FRAME];
    }

    public static void setGeneratorFunction(Object[] arguments, PFunction generatorFunction) {
        arguments[INDEX_GENERATOR_FRAME] = generatorFunction;
    }

    public static void setGeneratorFrameLocals(Object[] arguments, PDict locals) {
        arguments[INDEX_CALLER_FRAME_INFO] = locals;
    }

    public static PDict getGeneratorFrameLocals(Frame frame) {
        return getGeneratorFrameLocals(frame.getArguments());
    }

    public static PDict getGeneratorFrameLocals(Object[] arguments) {
        return (PDict) arguments[INDEX_CALLER_FRAME_INFO];
    }

    /**
     * Synchronizes the arguments array of a Truffle frame with a {@link PFrame}. Copies only those
     * arguments that are necessary to be synchronized between the two.
     *
     * NOTE: such arguments usually need to be preserved in {@link ThreadState} so that when we are
     * materializing a frame restored from {@link ThreadState}, the newly created instance of
     * {@link PFrame} will contain those arguments.
     */
    public static void synchronizeArgs(Frame frameToMaterialize, PFrame escapedFrame) {
        Object[] arguments = frameToMaterialize.getArguments();
        Object[] copiedArgs = new Object[arguments.length];

        // copy only some carefully picked internal arguments
        setSpecialArgument(copiedArgs, getSpecialArgument(arguments));
        setGeneratorFrame(copiedArgs, getGeneratorFrameSafe(arguments));
        setGlobals(copiedArgs, getGlobals(arguments));
        setClosure(copiedArgs, getClosure(arguments));

        // copy all user arguments
        PythonUtils.arraycopy(arguments, USER_ARGUMENTS_OFFSET, copiedArgs, USER_ARGUMENTS_OFFSET, getUserArgumentLength(arguments));

        escapedFrame.setArguments(copiedArgs);
    }

    public static ThreadState getThreadState(VirtualFrame frame) {
        assert frame != null : "cannot get thread state without a frame";
        return new ThreadState(PArguments.getCurrentFrameInfo(frame),
                        PArguments.getExceptionUnchecked(frame.getArguments()),
                        PArguments.getGlobals(frame));
    }

    public static ThreadState getThreadStateOrNull(VirtualFrame frame, ConditionProfile hasFrameProfile) {
        return hasFrameProfile.profile(frame != null) ? getThreadState(frame) : null;
    }

    public static VirtualFrame frameForCall(ThreadState frame) {
        Object[] args = PArguments.create();
        PArguments.setCurrentFrameInfo(args, frame.info);
        PArguments.setExceptionUnchecked(args, frame.exc);
        args[INDEX_GLOBALS_ARGUMENT] = frame.globals;
        return Truffle.getRuntime().createVirtualFrame(args, EMTPY_FD);
    }

    /**
     * Represents the current thread state information that needs to be passed between calls.
     */
    @ValueType
    public static final class ThreadState {
        private final PFrame.Reference info;
        // The type is object because it is Object in the frame and casting it slows things down
        private final Object exc;
        private final Object globals;

        private ThreadState(Reference info, Object exc, Object globals) {
            this.info = info;
            this.exc = exc;
            this.globals = globals;
        }
    }
}
