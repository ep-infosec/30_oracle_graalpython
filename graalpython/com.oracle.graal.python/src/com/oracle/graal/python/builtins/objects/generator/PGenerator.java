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
// skip GIL
package com.oracle.graal.python.builtins.objects.generator;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.nodes.bytecode.FrameInfo;
import com.oracle.graal.python.nodes.bytecode.GeneratorYieldResult;
import com.oracle.graal.python.nodes.bytecode.PBytecodeGeneratorRootNode;
import com.oracle.graal.python.nodes.bytecode.PBytecodeRootNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

public final class PGenerator extends PythonBuiltinObject {

    private TruffleString name;
    private TruffleString qualname;
    /**
     * Call targets with copies of the generator's AST. Each call target corresponds to one possible
     * entry point into the generator: the first call, and continuation for each yield. Each AST can
     * then specialize towards which nodes are executed when starting from that particular entry
     * point. When yielding, the next index to the next call target to continue from is updated via
     * {@link #handleResult}.
     */
    @CompilationFinal(dimensions = 1) protected final RootCallTarget[] callTargets;
    protected final Object[] arguments;
    private boolean finished;
    private PCode code;
    private int currentCallTarget;
    private final PBytecodeRootNode bytecodeRootNode;
    private final FrameInfo frameInfo;
    // running means it is currently on the stack, not just started
    private boolean running;

    public static PGenerator create(PythonLanguage lang, TruffleString name, TruffleString qualname, PBytecodeRootNode rootNode, RootCallTarget[] callTargets, Object[] arguments) {
        rootNode.createGeneratorFrame(arguments);
        return new PGenerator(lang, name, qualname, rootNode, callTargets, arguments);
    }

    private PGenerator(PythonLanguage lang, TruffleString name, TruffleString qualname, PBytecodeRootNode rootNode, RootCallTarget[] callTargets, Object[] arguments) {
        super(PythonBuiltinClassType.PGenerator, PythonBuiltinClassType.PGenerator.getInstanceShape(lang));
        this.name = name;
        this.qualname = qualname;
        this.callTargets = callTargets;
        this.currentCallTarget = 0;
        this.arguments = arguments;
        this.finished = false;
        this.bytecodeRootNode = rootNode;
        this.frameInfo = (FrameInfo) rootNode.getFrameDescriptor().getInfo();
    }

    public void handleResult(PythonLanguage language, GeneratorYieldResult result) {
        currentCallTarget = result.resumeBci;
        if (callTargets[currentCallTarget] == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            PBytecodeGeneratorRootNode rootNode = new PBytecodeGeneratorRootNode(language, bytecodeRootNode, result.resumeBci, result.resumeStackTop);
            callTargets[currentCallTarget] = rootNode.getCallTarget();
        }
    }

    /**
     * Returns the call target that should be used the next time the generator is called. Each time
     * a generator call target returns through a yield, the generator should be updated with the
     * next yield index to use via {@link #handleResult}
     */
    public RootCallTarget getCurrentCallTarget() {
        return callTargets[currentCallTarget];
    }

    public Object getYieldFrom() {
        if (running || finished) {
            return null;
        }
        return frameInfo.getYieldFrom(PArguments.getGeneratorFrame(arguments), getBci(), getCurrentRootNode().getResumeStackTop());
    }

    private PBytecodeGeneratorRootNode getCurrentRootNode() {
        return (PBytecodeGeneratorRootNode) getCurrentCallTarget().getRootNode();
    }

    public boolean isStarted() {
        return currentCallTarget != 0 && !running;
    }

    public int getBci() {
        if (!isStarted()) {
            return -1;
        } else if (finished) {
            return bytecodeRootNode.getCodeUnit().code.length;
        } else {
            return getCurrentRootNode().getResumeBci();
        }
    }

    public Object[] getArguments() {
        return arguments;
    }

    public boolean isFinished() {
        return finished;
    }

    public void markAsFinished() {
        finished = true;
    }

    @Override
    public String toString() {
        return "<generator object " + name + " at " + hashCode() + ">";
    }

    public PCode getOrCreateCode(ConditionProfile hasCodeProfile, PythonObjectFactory factory) {
        if (hasCodeProfile.profile(code == null)) {
            RootCallTarget callTarget;
            callTarget = bytecodeRootNode.getCallTarget();
            code = factory.createCode(callTarget);
        }
        return code;
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        assert !running || !this.running : "Attempted to set an already running generator as running";
        this.running = running;
    }

    public TruffleString getName() {
        return name;
    }

    public void setName(TruffleString name) {
        this.name = name;
    }

    public TruffleString getQualname() {
        return qualname;
    }

    public void setQualname(TruffleString qualname) {
        this.qualname = qualname;
    }
}
