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

import static com.oracle.graal.python.nodes.StringLiterals.T_COMMA;
import static com.oracle.graal.python.nodes.StringLiterals.T_SLASH;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithRaiseAndIndirectCall;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.BufferToTruffleStringNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.Encoding;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

@CoreFunctions(defineModule = "_sre")
public class SREModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SREModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        addBuiltinConstant("_with_tregex", core.getContext().getLanguage().getEngineOption(PythonOptions.WithTRegex));
        addBuiltinConstant("_with_sre", core.getContext().getLanguage().getEngineOption(PythonOptions.TRegexUsesSREFallback));
        super.initialize(core);
    }

    abstract static class ToRegexSourceNode extends PNodeWithRaiseAndIndirectCall {

        private static final TruffleString T_FLAVOR_PYTHON = tsLiteral("Flavor=Python");
        private static final TruffleString T_ENCODING_UTF_32 = tsLiteral("Encoding=UTF-32");
        private static final TruffleString T_ENCODING_LATIN_1 = tsLiteral("Encoding=LATIN-1");

        public abstract Source execute(VirtualFrame frame, Object pattern, TruffleString flags, TruffleString options);

        private static Source constructRegexSource(TruffleString encoding, TruffleString options, TruffleString pattern, TruffleString flags, ConditionProfile nonEmptyOptionsProfile,
                        TruffleStringBuilder.AppendStringNode appendStringNode, TruffleStringBuilder.ToStringNode toStringNode, TruffleString.ToJavaStringNode toJavaStringNode) {
            TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING);
            appendStringNode.execute(sb, T_FLAVOR_PYTHON);
            appendStringNode.execute(sb, T_COMMA);
            appendStringNode.execute(sb, encoding);
            if (nonEmptyOptionsProfile.profile(!options.isEmpty())) {
                appendStringNode.execute(sb, T_COMMA);
                appendStringNode.execute(sb, options);
            }
            appendStringNode.execute(sb, T_SLASH);
            appendStringNode.execute(sb, pattern);
            appendStringNode.execute(sb, T_SLASH);
            appendStringNode.execute(sb, flags);
            return createSourceBoundary(toJavaStringNode.execute(toStringNode.execute(sb)));
        }

        @TruffleBoundary
        private static Source createSourceBoundary(String regexSourceStr) {
            return Source.newBuilder("regex", regexSourceStr, "re").mimeType("application/tregex").internal(true).build();
        }

        @Specialization
        protected Source doString(TruffleString pattern, TruffleString flags, TruffleString options,
                        @Shared("nonEmptyOptions") @Cached ConditionProfile nonEmptyOptionsProfile,
                        @Shared("appendStr") @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Shared("toString") @Cached TruffleStringBuilder.ToStringNode toStringNode,
                        @Shared("ts2js") @Cached TruffleString.ToJavaStringNode toJavaStringNode) {
            return constructRegexSource(T_ENCODING_UTF_32, options, pattern, flags, nonEmptyOptionsProfile, appendStringNode, toStringNode, toJavaStringNode);
        }

        @Specialization
        protected Source doGeneric(VirtualFrame frame, Object pattern, TruffleString flags, TruffleString options,
                        @Shared("nonEmptyOptions") @Cached ConditionProfile nonEmptyOptionsProfile,
                        @Shared("appendStr") @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Shared("toString") @Cached TruffleStringBuilder.ToStringNode toStringNode,
                        @Shared("ts2js") @Cached TruffleString.ToJavaStringNode toJavaStringNode,
                        @Cached CastToTruffleStringNode cast,
                        @CachedLibrary(limit = "3") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @Cached TruffleString.FromByteArrayNode fromByteArrayNode) {
            try {
                return doString(cast.execute(pattern), flags, options, nonEmptyOptionsProfile, appendStringNode, toStringNode, toJavaStringNode);
            } catch (CannotCastException ce) {
                Object buffer;
                try {
                    buffer = bufferAcquireLib.acquireReadonly(pattern, frame, this);
                } catch (PException e) {
                    throw raise(TypeError, ErrorMessages.EXPECTED_STR_OR_BYTESLIKE_OBJ);
                }
                try {
                    byte[] bytes = bufferLib.getInternalOrCopiedByteArray(buffer);
                    int bytesLen = bufferLib.getBufferLength(buffer);
                    TruffleString patternStr = fromByteArrayNode.execute(bytes, 0, bytesLen, Encoding.ISO_8859_1, false);
                    return constructRegexSource(T_ENCODING_LATIN_1, options, patternStr, flags, nonEmptyOptionsProfile, appendStringNode, toStringNode, toJavaStringNode);
                } finally {
                    bufferLib.release(buffer, frame, this);
                }
            }
        }
    }

    @Builtin(name = "tregex_compile_internal", minNumOfPositionalArgs = 3)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class TRegexCallCompile extends PythonTernaryBuiltinNode {

        @Specialization
        Object call(VirtualFrame frame, Object pattern, Object flags, Object options,
                        @Cached BranchProfile potentialSyntaxError,
                        @Cached BranchProfile syntaxError,
                        @Cached BranchProfile unsupportedRegexError,
                        @Cached CastToTruffleStringNode flagsToStringNode,
                        @Cached CastToTruffleStringNode optionsToStringNode,
                        @Cached ToRegexSourceNode toRegexSourceNode,
                        @CachedLibrary(limit = "2") InteropLibrary exceptionLib,
                        @CachedLibrary(limit = "2") InteropLibrary compiledRegexLib,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode) {
            try {
                TruffleString flagsStr = flagsToStringNode.execute(flags);
                TruffleString optionsStr = optionsToStringNode.execute(options);
                Source regexSource = toRegexSourceNode.execute(frame, pattern, flagsStr, optionsStr);
                Object compiledRegex = getContext().getEnv().parseInternal(regexSource).call();
                if (compiledRegexLib.isNull(compiledRegex)) {
                    unsupportedRegexError.enter();
                    return PNone.NONE;
                } else {
                    return compiledRegex;
                }
            } catch (RuntimeException e) {
                return handleError(e, syntaxError, potentialSyntaxError, exceptionLib, switchEncodingNode);
            }
        }

        private Object handleError(RuntimeException e, BranchProfile syntaxError, BranchProfile potentialSyntaxError, InteropLibrary lib, TruffleString.SwitchEncodingNode switchEncodingNode) {
            try {
                if (lib.isException(e)) {
                    potentialSyntaxError.enter();
                    if (lib.getExceptionType(e) == ExceptionType.PARSE_ERROR) {
                        syntaxError.enter();
                        TruffleString reason = switchEncodingNode.execute(lib.asTruffleString(lib.getExceptionMessage(e)), TS_ENCODING);
                        SourceSection sourceSection = lib.getSourceLocation(e);
                        int position = sourceSection.getCharIndex();
                        // passed like Object array concats the values
                        throw raise(ValueError, new Object[]{reason, position});
                    }
                }
            } catch (UnsupportedMessageException e1) {
                throw CompilerDirectives.shouldNotReachHere();
            }
            // just re-throw
            throw e;
        }
    }

    @Builtin(name = "tregex_call_exec", minNumOfPositionalArgs = 3)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class TRegexCallExec extends PythonTernaryBuiltinNode {

        @Specialization(limit = "1")
        Object call(VirtualFrame frame, Object callable, Object inputStringOrBytes, Number fromIndex,
                        @Cached CastToTruffleStringNode cast,
                        @CachedLibrary(limit = "3") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @Cached BufferToTruffleStringNode bufferToTruffleStringNode,
                        @CachedLibrary("callable") InteropLibrary interop) {
            PythonContext context = getContext();
            PythonLanguage language = getLanguage();
            TruffleString input;
            Object buffer = null;
            try {
                try {
                    // This would materialize the string if it was native
                    input = cast.execute(inputStringOrBytes);
                } catch (CannotCastException e1) {
                    // It's bytes or other buffer object
                    buffer = bufferAcquireLib.acquireReadonly(inputStringOrBytes, frame, this);
                    input = bufferToTruffleStringNode.execute(buffer, 0);
                }
                Object state = IndirectCallContext.enter(frame, language, context, this);
                try {
                    return interop.execute(callable, input, fromIndex);
                } catch (ArityException | UnsupportedTypeException | UnsupportedMessageException e2) {
                    throw CompilerDirectives.shouldNotReachHere("could not call TRegex exec method", e2);
                } finally {
                    IndirectCallContext.exit(frame, language, context, state);
                }
            } finally {
                if (buffer != null) {
                    bufferLib.release(buffer, frame, this);
                }
            }
        }
    }
}
