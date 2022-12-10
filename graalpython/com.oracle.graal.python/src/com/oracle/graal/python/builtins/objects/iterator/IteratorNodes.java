/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.iterator;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PIterator;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___LENGTH_HINT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___LEN__;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.GetSequenceStorageNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.iterator.IteratorNodesFactory.GetInternalIteratorSequenceStorageNodeGen;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.lib.GetNextNode;
import com.oracle.graal.python.lib.PyIndexCheckNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PNodeWithRaise;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.LookupCallableSlotInMRONode;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodSlotNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.LoopConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringIterator;

public abstract class IteratorNodes {

    /**
     * Implements the logic from {@code PyObject_LengthHint}. This returns a non-negative value from
     * o.__len__() or o.__length_hint__(). If those methods aren't found the defaultvalue is
     * returned. If either has no viable or defaultvalue implementation then this node returns -1.
     */
    @GenerateNodeFactory
    @ImportStatic({PGuards.class, SpecialMethodNames.class, SpecialMethodSlot.class})
    public abstract static class GetLength extends PNodeWithContext {

        public abstract int execute(VirtualFrame frame, Object iterable);

        @Specialization(guards = {"isString(iterable)"})
        int length(VirtualFrame frame, Object iterable,
                        @Cached PyObjectSizeNode sizeNode) {
            return sizeNode.execute(frame, iterable);
        }

        @Specialization(guards = {"isNoValue(iterable)"})
        static int length(@SuppressWarnings({"unused"}) VirtualFrame frame, @SuppressWarnings({"unused"}) Object iterable) {
            return -1;
        }

        @Specialization(guards = {"!isNoValue(iterable)", "!isString(iterable)"}, limit = "4")
        int length(VirtualFrame frame, Object iterable,
                        @CachedLibrary("iterable") InteropLibrary iLib,
                        @Cached GetClassNode getClassNode,
                        @Cached PyIndexCheckNode indexCheckNode,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached("create(Len)") LookupCallableSlotInMRONode lenNode,
                        @Cached("create(LengthHint)") LookupSpecialMethodSlotNode lenHintNode,
                        @Cached CallUnaryMethodNode dispatchLenOrLenHint,
                        @Cached IsBuiltinClassProfile errorProfile,
                        @Cached ConditionProfile hasLenProfile,
                        @Cached ConditionProfile hasLengthHintProfile,
                        @Cached PRaiseNode raiseNode,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached GilNode gil) {
            if (iLib.isString(iterable)) {
                gil.release(true);
                try {
                    return codePointLengthNode.execute(switchEncodingNode.execute(iLib.asTruffleString(iterable), TS_ENCODING), TS_ENCODING);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere();
                } finally {
                    gil.acquire();
                }
            }
            Object clazz = getClassNode.execute(iterable);
            Object attrLenObj = lenNode.execute(clazz);
            if (hasLenProfile.profile(attrLenObj != PNone.NO_VALUE)) {
                Object len = null;
                try {
                    len = dispatchLenOrLenHint.executeObject(frame, attrLenObj, iterable);
                } catch (PException e) {
                    e.expect(TypeError, errorProfile);
                }
                if (len != null && len != PNotImplemented.NOT_IMPLEMENTED) {
                    if (indexCheckNode.execute(len)) {
                        int intLen = asSizeNode.executeExact(frame, len);
                        if (intLen < 0) {
                            throw raiseNode.raise(TypeError, ErrorMessages.LEN_SHOULD_RETURN_GT_ZERO);
                        }
                        return intLen;
                    } else {
                        throw raiseNode.raise(TypeError, ErrorMessages.MUST_BE_INTEGER_NOT_P, T___LEN__, len);
                    }
                }
            }
            Object attrLenHintObj = lenHintNode.execute(frame, clazz, iterable);
            if (hasLengthHintProfile.profile(attrLenHintObj != PNone.NO_VALUE)) {
                Object len = null;
                try {
                    len = dispatchLenOrLenHint.executeObject(frame, attrLenHintObj, iterable);
                } catch (PException e) {
                    e.expect(TypeError, errorProfile);
                }
                if (len != null && len != PNotImplemented.NOT_IMPLEMENTED) {
                    if (indexCheckNode.execute(len)) {
                        int intLen = asSizeNode.executeExact(frame, len);
                        if (intLen < 0) {
                            throw raiseNode.raise(TypeError, ErrorMessages.LENGTH_HINT_SHOULD_RETURN_MT_ZERO);
                        }
                        return intLen;
                    } else {
                        throw raiseNode.raise(TypeError, ErrorMessages.MUST_BE_INTEGER_NOT_P, T___LENGTH_HINT__, len);
                    }
                }
            }
            return -1;
        }
    }

    @ImportStatic(PGuards.class)
    @GenerateUncached
    public abstract static class GetInternalIteratorSequenceStorage extends Node {
        public static GetInternalIteratorSequenceStorage getUncached() {
            return GetInternalIteratorSequenceStorageNodeGen.getUncached();
        }

        /**
         * The argument must be a builtin iterator, which points to the first element of the
         * internal sequence storage. Returns {@code null} if the sequence storage is not available
         * or if the iterator is not pointing to the first item in the storage.
         */
        public final SequenceStorage execute(PBuiltinIterator iterator) {
            assert GetClassNode.getUncached().execute(iterator) == PIterator;
            assert iterator.index == 0 && !iterator.isExhausted();
            return executeInternal(iterator);
        }

        protected abstract SequenceStorage executeInternal(PBuiltinIterator iterator);

        @Specialization(guards = "isList(it.sequence)")
        static SequenceStorage doSequenceList(PSequenceIterator it) {
            return ((PList) it.sequence).getSequenceStorage();
        }

        @Specialization
        static SequenceStorage doSequenceLong(PLongSequenceIterator it) {
            return it.sequence;
        }

        @Specialization
        static SequenceStorage doSequenceDouble(PDoubleSequenceIterator it) {
            return it.sequence;
        }

        @Specialization
        static SequenceStorage doSequenceObj(PObjectSequenceIterator it) {
            return it.sequence;
        }

        @Specialization
        static SequenceStorage doSequenceIntSeq(PIntegerSequenceIterator it) {
            return it.sequence;
        }

        @Specialization(guards = "isPTuple(it.sequence)")
        static SequenceStorage doSequenceTuple(PSequenceIterator it) {
            return ((PTuple) it.sequence).getSequenceStorage();
        }

        @Fallback
        static SequenceStorage doOthers(@SuppressWarnings("unused") PBuiltinIterator it) {
            return null;
        }
    }

    @ImportStatic(PGuards.class)
    public abstract static class BuiltinIteratorLengthHint extends Node {
        /**
         * The argument must be a builtin iterator. Returns {@code -1} if the length hint is not
         * available and rewrites itself to generic fallback that always returns {@code -1}.
         */
        public final int execute(PBuiltinIterator iterator) {
            assert GetClassNode.getUncached().execute(iterator) == PIterator;
            return executeInternal(iterator);
        }

        protected abstract int executeInternal(PBuiltinIterator iterator);

        protected static SequenceStorage getStorage(GetInternalIteratorSequenceStorage getSeqStorage, PBuiltinIterator it) {
            return it.index != 0 || it.isExhausted() ? null : getSeqStorage.execute(it);
        }

        @Specialization(guards = "storage != null")
        static int doSeqStorage(@SuppressWarnings("unused") PBuiltinIterator it,
                        @SuppressWarnings("unused") @Cached GetInternalIteratorSequenceStorage getSeqStorage,
                        @Bind("getStorage(getSeqStorage, it)") SequenceStorage storage) {
            return ensurePositive(storage.length());
        }

        @Specialization
        static int doString(PStringIterator it,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode) {
            return ensurePositive(codePointLengthNode.execute(it.value, TS_ENCODING));
        }

        @Specialization
        static int doSequenceArr(PArrayIterator it) {
            return ensurePositive(it.array.getLength());
        }

        @Specialization
        static int doSequenceIntRange(PIntRangeIterator it) {
            return ensurePositive(it.getRemainingLength());
        }

        @Specialization(replaces = {"doSeqStorage", "doString", "doSequenceArr", "doSequenceIntRange"})
        static int doGeneric(@SuppressWarnings("unused") PBuiltinIterator it) {
            return -1;
        }

        static int ensurePositive(int len) {
            if (len < 0) {
                throw CompilerDirectives.shouldNotReachHere();
            }
            return len;
        }
    }

    @GenerateUncached
    public abstract static class IsIteratorObjectNode extends Node {

        public abstract boolean execute(Object o);

        @Specialization
        static boolean doPIterator(@SuppressWarnings("unused") PBuiltinIterator it) {
            // a PIterator object is guaranteed to be an iterator object
            return true;
        }

        @Specialization
        static boolean doGeneric(Object it,
                        @Cached LookupInheritedAttributeNode.Dynamic lookupAttributeNode) {
            return lookupAttributeNode.execute(it, SpecialMethodNames.T___NEXT__) != PNone.NO_VALUE;
        }
    }

    public abstract static class ToArrayNode extends PNodeWithRaise {
        public abstract Object[] execute(VirtualFrame frame, Object iterable);

        @Specialization
        public static Object[] doIt(TruffleString iterable,
                        @Cached LoopConditionProfile loopProfile,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.CreateCodePointIteratorNode createCodePointIteratorNode,
                        @Cached TruffleStringIterator.NextNode nextNode,
                        @Cached TruffleString.FromCodePointNode fromCodePointNode) {
            Object[] result = new Object[codePointLengthNode.execute(iterable, TS_ENCODING)];
            loopProfile.profileCounted(result.length);
            TruffleStringIterator it = createCodePointIteratorNode.execute(iterable, TS_ENCODING);
            int i = 0;
            while (loopProfile.inject(it.hasNext())) {
                // TODO: GR-37219: use SubstringNode with lazy=true?
                result[i++] = fromCodePointNode.execute(nextNode.execute(it), TS_ENCODING, true);
            }
            return result;
        }

        @Specialization
        public static Object[] doIt(PString iterable,
                        @Cached CastToTruffleStringNode castToStringNode,
                        @Cached LoopConditionProfile loopProfile,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.CreateCodePointIteratorNode createCodePointIteratorNode,
                        @Cached TruffleStringIterator.NextNode nextNode,
                        @Cached TruffleString.FromCodePointNode fromCodePointNode) {
            return doIt(castToStringNode.execute(iterable), loopProfile, codePointLengthNode, createCodePointIteratorNode, nextNode, fromCodePointNode);
        }

        @Specialization
        public static Object[] doIt(PSequence iterable,
                        @Cached GetSequenceStorageNode getStorageNode,
                        @Cached SequenceStorageNodes.ToArrayNode toArrayNode) {
            SequenceStorage storage = getStorageNode.execute(iterable);
            return toArrayNode.execute(storage);
        }

        @Specialization(guards = {"!isPSequence(iterable)", "!isString(iterable)"})
        public static Object[] doIt(VirtualFrame frame, Object iterable,
                        @Cached GetNextNode getNextNode,
                        @Cached IsBuiltinClassProfile stopIterationProfile,
                        @Cached PyObjectGetIter getIter) {
            Object it = getIter.execute(frame, iterable);
            List<Object> result = createlist();
            while (true) {
                try {
                    result.add(getNextNode.execute(frame, it));
                } catch (PException e) {
                    e.expectStopIteration(stopIterationProfile);
                    return result.toArray(new Object[result.size()]);
                }
            }
        }

        @TruffleBoundary
        private static List<Object> createlist() {
            return new ArrayList<>();
        }
    }
}
