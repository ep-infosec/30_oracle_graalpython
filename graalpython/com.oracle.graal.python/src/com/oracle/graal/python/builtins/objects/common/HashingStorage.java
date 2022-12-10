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
package com.oracle.graal.python.builtins.objects.common;

import static com.oracle.graal.python.nodes.SpecialMethodNames.T_KEYS;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorageFactory.InitNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageAddAllToOther;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageCopy;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.LenNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.lib.GetNextNode;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.IndirectCallNode;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.LookupCallableSlotInMRONode;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.builtins.ListNodes.FastConstructListNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.util.ArrayBuilder;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class HashingStorage {
    public abstract static class InitNode extends PNodeWithContext implements IndirectCallNode {

        private final Assumption dontNeedExceptionState = Truffle.getRuntime().createAssumption();
        private final Assumption dontNeedCallerFrame = Truffle.getRuntime().createAssumption();

        @Override
        public Assumption needNotPassFrameAssumption() {
            return dontNeedCallerFrame;
        }

        @Override
        public Assumption needNotPassExceptionAssumption() {
            return dontNeedExceptionState;
        }

        public abstract HashingStorage execute(VirtualFrame frame, Object mapping, PKeyword[] kwargs);

        @Child private LookupInheritedAttributeNode lookupKeysAttributeNode;

        protected boolean isEmpty(PKeyword[] kwargs) {
            return kwargs.length == 0;
        }

        @Specialization(guards = {"isNoValue(iterable)", "isEmpty(kwargs)"})
        static HashingStorage doEmpty(@SuppressWarnings("unused") PNone iterable, @SuppressWarnings("unused") PKeyword[] kwargs) {
            return EmptyStorage.INSTANCE;
        }

        @Specialization(guards = {"isNoValue(iterable)", "!isEmpty(kwargs)"})
        static HashingStorage doKeywords(@SuppressWarnings("unused") PNone iterable, PKeyword[] kwargs) {
            return new KeywordsStorage(kwargs);
        }

        protected static boolean isPDict(Object o) {
            return o instanceof PHashingCollection;
        }

        protected boolean hasKeysAttribute(Object o) {
            if (lookupKeysAttributeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupKeysAttributeNode = insert(LookupInheritedAttributeNode.create(T_KEYS));
            }
            return lookupKeysAttributeNode.execute(o) != PNone.NO_VALUE;
        }

        @Specialization(guards = {"isEmpty(kwargs)", "!hasIterAttrButNotBuiltin(dictLike, getClassNode, lookupIter)"}, limit = "1")
        static HashingStorage doPDict(PHashingCollection dictLike, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @SuppressWarnings("unused") @Shared("getClass") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared("lookupIter") @Cached(parameters = "Iter") LookupCallableSlotInMRONode lookupIter,
                        @Shared("copy") @Cached HashingStorageCopy copyNode) {
            return copyNode.execute(dictLike.getDictStorage());
        }

        @Specialization(guards = {"!isEmpty(kwargs)", "!hasIterAttrButNotBuiltin(iterable, getClassNode, lookupIter)"}, limit = "1")
        HashingStorage doPDictKwargs(VirtualFrame frame, PHashingCollection iterable, PKeyword[] kwargs,
                        @SuppressWarnings("unused") @Shared("getClass") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared("lookupIter") @Cached(parameters = "Iter") LookupCallableSlotInMRONode lookupIter,
                        @Shared("copy") @Cached HashingStorageCopy copyNode,
                        @Cached HashingStorageAddAllToOther addAllToOther) {
            PythonContext contextRef = PythonContext.get(this);
            PythonLanguage language = PythonLanguage.get(this);
            Object state = IndirectCallContext.enter(frame, language, contextRef, this);
            try {
                HashingStorage iterableDictStorage = iterable.getDictStorage();
                HashingStorage dictStorage = copyNode.execute(iterableDictStorage);
                return addAllToOther.execute(frame, new KeywordsStorage(kwargs), dictStorage);
            } finally {
                IndirectCallContext.exit(frame, language, contextRef, state);
            }
        }

        @Specialization(guards = "hasIterAttrButNotBuiltin(col, getClassNode, lookupIter)", limit = "1")
        HashingStorage doNoBuiltinKeysAttr(VirtualFrame frame, PHashingCollection col, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @SuppressWarnings("unused") @Shared("getClass") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared("lookupIter") @Cached(parameters = "Iter") LookupCallableSlotInMRONode lookupIter,
                        @Shared("getIter") @Cached PyObjectGetIter getIter,
                        @Shared("setStorageItem") @Cached HashingStorageSetItem setHasihngStorageItem,
                        @Shared("addAllToOther") @Cached HashingStorageAddAllToOther addAllToOther,
                        @Shared("callKeys") @Cached("create(T_KEYS)") LookupAndCallUnaryNode callKeysNode,
                        @Shared("getItem") @Cached PyObjectGetItem getItemNode,
                        @Shared("getNext") @Cached GetNextNode nextNode,
                        @Shared("errorProfile") @Cached IsBuiltinClassProfile errorProfile) {
            HashingStorage curStorage = PDict.createNewStorage(0);
            return copyToStorage(frame, col, kwargs, curStorage, callKeysNode, getItemNode, getIter, nextNode, errorProfile, setHasihngStorageItem, addAllToOther);
        }

        protected static boolean hasIterAttrButNotBuiltin(PHashingCollection col, GetClassNode getClassNode, LookupCallableSlotInMRONode lookupIter) {
            Object attr = lookupIter.execute(getClassNode.execute(col));
            return attr != PNone.NO_VALUE && !(attr instanceof PBuiltinMethod || attr instanceof PBuiltinFunction);
        }

        @Specialization(guards = {"!isPDict(mapping)", "hasKeysAttribute(mapping)"})
        HashingStorage doMapping(VirtualFrame frame, Object mapping, PKeyword[] kwargs,
                        @Shared("setStorageItem") @Cached HashingStorageSetItem setHasihngStorageItem,
                        @Shared("addAllToOther") @Cached HashingStorageAddAllToOther addAllToOther,
                        @Shared("getIter") @Cached PyObjectGetIter getIter,
                        @Shared("callKeys") @Cached("create(T_KEYS)") LookupAndCallUnaryNode callKeysNode,
                        @Shared("getItem") @Cached PyObjectGetItem getItemNode,
                        @Shared("getNext") @Cached GetNextNode nextNode,
                        @Shared("errorProfile") @Cached IsBuiltinClassProfile errorProfile) {
            HashingStorage curStorage = PDict.createNewStorage(0);
            return copyToStorage(frame, mapping, kwargs, curStorage, callKeysNode, getItemNode, getIter, nextNode, errorProfile, setHasihngStorageItem, addAllToOther);
        }

        @Specialization(guards = {"!isNoValue(iterable)", "!isPDict(iterable)", "!hasKeysAttribute(iterable)"})
        HashingStorage doSequence(VirtualFrame frame, Object iterable, PKeyword[] kwargs,
                        @Shared("setStorageItem") @Cached HashingStorageSetItem setHasihngStorageItem,
                        @Shared("addAllToOther") @Cached HashingStorageAddAllToOther addAllToOther,
                        @Shared("getIter") @Cached PyObjectGetIter getIter,
                        @Cached PRaiseNode raise,
                        @Shared("getNext") @Cached GetNextNode nextNode,
                        @Cached FastConstructListNode createListNode,
                        @Shared("getItem") @Cached PyObjectGetItem getItemNode,
                        @Cached SequenceNodes.LenNode seqLenNode,
                        @Cached ConditionProfile lengthTwoProfile,
                        @Shared("errorProfile") @Cached IsBuiltinClassProfile errorProfile,
                        @Cached IsBuiltinClassProfile isTypeErrorProfile) {

            return addSequenceToStorage(frame, iterable, kwargs, PDict::createNewStorage, getIter, nextNode, createListNode,
                            seqLenNode, lengthTwoProfile, raise, getItemNode, isTypeErrorProfile,
                            errorProfile, setHasihngStorageItem, addAllToOther);
        }

        public static InitNode create() {
            return InitNodeGen.create();
        }
    }

    public final HashingStorage union(HashingStorage other, HashingStorageCopy copyNode, HashingStorageAddAllToOther addAllToOther) {
        HashingStorage newStore = copyNode.execute(this);
        return addAllToOther.execute(null, other, newStore);
    }

    /**
     * Adds all items from the given mapping object to storage. It is the caller responsibility to
     * ensure, that mapping has the 'keys' attribute.
     */
    public static HashingStorage copyToStorage(VirtualFrame frame, Object mapping, PKeyword[] kwargs, HashingStorage storage,
                    LookupAndCallUnaryNode callKeysNode, PyObjectGetItem callGetItemNode,
                    PyObjectGetIter getIter, GetNextNode nextNode,
                    IsBuiltinClassProfile errorProfile, HashingStorageSetItem setHashingStorageItem, HashingStorageAddAllToOther addAllToOtherNode) {
        Object keysIterable = callKeysNode.executeObject(frame, mapping);
        Object keysIt = getIter.execute(frame, keysIterable);
        HashingStorage curStorage = storage;
        while (true) {
            try {
                Object keyObj = nextNode.execute(frame, keysIt);
                Object valueObj = callGetItemNode.execute(frame, mapping, keyObj);

                curStorage = setHashingStorageItem.execute(frame, curStorage, keyObj, valueObj);
            } catch (PException e) {
                e.expectStopIteration(errorProfile);
                break;
            }
        }
        if (kwargs.length > 0) {
            curStorage = addAllToOtherNode.execute(frame, new KeywordsStorage(kwargs), curStorage);
        }
        return curStorage;
    }

    @FunctionalInterface
    public interface StorageSupplier {
        HashingStorage get(int length);
    }

    public static HashingStorage addSequenceToStorage(VirtualFrame frame, Object iterable, PKeyword[] kwargs, StorageSupplier storageSupplier,
                    PyObjectGetIter getIter, GetNextNode nextNode, FastConstructListNode createListNode, LenNode seqLenNode,
                    ConditionProfile lengthTwoProfile, PRaiseNode raise, PyObjectGetItem getItemNode, IsBuiltinClassProfile isTypeErrorProfile,
                    IsBuiltinClassProfile errorProfile, HashingStorageSetItem setHashingStorageItem, HashingStorageAddAllToOther addAllToOther) throws PException {
        Object it = getIter.execute(frame, iterable);
        ArrayBuilder<PSequence> elements = new ArrayBuilder<>();
        try {
            while (true) {
                Object next = nextNode.execute(frame, it);
                PSequence element = createListNode.execute(frame, next);
                assert element != null;
                // This constructs a new list using the builtin type. So, the object cannot
                // be subclassed and we can directly call 'len()'.
                int len = seqLenNode.execute(element);

                if (lengthTwoProfile.profile(len != 2)) {
                    throw raise.raise(ValueError, ErrorMessages.DICT_UPDATE_SEQ_ELEM_HAS_LENGTH_2_REQUIRED, elements.size(), len);
                }

                elements.add(element);
            }
        } catch (PException e) {
            if (isTypeErrorProfile.profileException(e, TypeError)) {
                throw raise.raise(TypeError, ErrorMessages.CANNOT_CONVERT_DICT_UPDATE_SEQ, elements.size());
            } else {
                e.expectStopIteration(errorProfile);
            }
        }
        HashingStorage storage = storageSupplier.get(elements.size() + kwargs.length);
        for (int j = 0; j < elements.size(); j++) {
            PSequence element = elements.get(j);
            Object key = getItemNode.execute(frame, element, 0);
            Object value = getItemNode.execute(frame, element, 1);
            storage = setHashingStorageItem.execute(frame, storage, key, value);
        }
        if (kwargs.length > 0) {
            storage = addAllToOther.execute(frame, new KeywordsStorage(kwargs), storage);
        }
        return storage;
    }
}
