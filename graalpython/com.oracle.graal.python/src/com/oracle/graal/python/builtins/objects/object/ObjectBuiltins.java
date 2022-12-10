/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
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

package com.oracle.graal.python.builtins.objects.object;

import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___CLASS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___DICT__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___BASICSIZE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___CLASS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DICT__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___ITEMSIZE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J_RICHCMP;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___DELATTR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___DIR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___FORMAT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GETATTRIBUTE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___HASH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT_SUBCLASS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE_EX__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SETATTR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SIZEOF__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___STR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SUBCLASSHOOK__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_UPDATE;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___NE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___REDUCE__;
import static com.oracle.graal.python.nodes.StringLiterals.T_NONE;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.AttributeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinConstructorsFactory;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptor;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.DescriptorBuiltins.DescrDeleteNode;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.DescriptorBuiltins.DescrGetNode;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.DescriptorBuiltins.DescrSetNode;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.DescriptorDeleteMarker;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltinsClinicProviders.FormatNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltinsClinicProviders.ReduceExNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltinsFactory.GetAttributeNodeFactory;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.CheckCompatibleForAssigmentNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetBaseClassNode;
import com.oracle.graal.python.lib.PyLongAsLongNode;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.LookupCallableSlotInMRONode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallTernaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNodeFactory;
import com.oracle.graal.python.nodes.expression.CoerceToBooleanNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.DeleteDictNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetDictIfExistsNode;
import com.oracle.graal.python.nodes.object.GetOrCreateDictNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.object.IsNode;
import com.oracle.graal.python.nodes.object.SetDictNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.nodes.util.SplitArgsNode;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PythonObject)
public class ObjectBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ObjectBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___CLASS__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class ClassNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "isNoValue(value)")
        static Object getClass(Object self, @SuppressWarnings("unused") PNone value,
                        @Cached GetClassNode getClass) {
            return getClass.execute(self);
        }

        @Specialization(guards = "isNativeClass(klass)")
        Object setClass(@SuppressWarnings("unused") Object self, @SuppressWarnings("unused") Object klass) {
            throw raise(TypeError, ErrorMessages.CLASS_ASSIGNMENT_ONLY_SUPPORTED_FOR_HEAP_TYPES_OR_MODTYPE_SUBCLASSES);
        }

        @Specialization(guards = "isPythonClass(value) || isPythonBuiltinClassType(value)")
        PNone setClass(VirtualFrame frame, PythonObject self, Object value,
                        @CachedLibrary(limit = "4") DynamicObjectLibrary dylib,
                        @Cached IsBuiltinClassProfile classProfile1,
                        @Cached IsBuiltinClassProfile classProfile2,
                        @Cached CheckCompatibleForAssigmentNode checkCompatibleForAssigmentNode,
                        @Cached GetClassNode getClassNode) {
            Object type = getClassNode.execute(self);
            if (isBuiltinClassNotModule(value, classProfile1) || PGuards.isNativeClass(value) || isBuiltinClassNotModule(type, classProfile2) || PGuards.isNativeClass(type)) {
                throw raise(TypeError, ErrorMessages.CLASS_ASSIGNMENT_ONLY_SUPPORTED_FOR_HEAP_TYPES_OR_MODTYPE_SUBCLASSES);
            }

            checkCompatibleForAssigmentNode.execute(frame, type, value);
            self.setPythonClass(value, dylib);
            return PNone.NONE;
        }

        private static boolean isBuiltinClassNotModule(Object type, IsBuiltinClassProfile classProfile) {
            return classProfile.profileIsAnyBuiltinClass(type) && !classProfile.profileClass(type, PythonBuiltinClassType.PythonModule);
        }

        @Specialization(guards = {"isPythonClass(value) || isPythonBuiltinClassType(value)", "!isPythonObject(self)"})
        Object getClass(@SuppressWarnings("unused") Object self, @SuppressWarnings("unused") Object value) {
            throw raise(TypeError, ErrorMessages.CLASS_ASSIGNMENT_ONLY_SUPPORTED_FOR_HEAP_TYPES_OR_MODTYPE_SUBCLASSES);
        }

        @Fallback
        Object getClassError(@SuppressWarnings("unused") Object self, Object value) {
            throw raise(TypeError, ErrorMessages.CLASS_MUST_BE_SET_TO_CLASS, value);
        }
    }

    @Builtin(name = J___INIT__, takesVarArgs = true, minNumOfPositionalArgs = 1, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    @ImportStatic(SpecialMethodSlot.class)
    public abstract static class InitNode extends PythonVarargsBuiltinNode {
        @Child private SplitArgsNode splitArgsNode;

        @Override
        public final Object varArgExecute(VirtualFrame frame, @SuppressWarnings("unused") Object self, Object[] arguments, PKeyword[] keywords) throws VarargsBuiltinDirectInvocationNotSupported {
            if (splitArgsNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                splitArgsNode = insert(SplitArgsNode.create());
            }
            return execute(frame, arguments[0], splitArgsNode.execute(arguments), keywords);
        }

        @Specialization(guards = {"arguments.length == 0", "keywords.length == 0"})
        @SuppressWarnings("unused")
        static PNone initNoArgs(Object self, Object[] arguments, PKeyword[] keywords) {
            return PNone.NONE;
        }

        @Specialization(replaces = "initNoArgs")
        @SuppressWarnings("unused")
        PNone init(Object self, Object[] arguments, PKeyword[] keywords,
                        @Cached GetClassNode getClassNode,
                        @Cached ConditionProfile overridesNew,
                        @Cached ConditionProfile overridesInit,
                        @Cached("create(Init)") LookupCallableSlotInMRONode lookupInit,
                        @Cached("createLookupProfile(getClassNode)") ValueProfile profileInit,
                        @Cached("createClassProfile()") ValueProfile profileInitFactory,
                        @Cached("create(New)") LookupCallableSlotInMRONode lookupNew,
                        @Cached("createLookupProfile(getClassNode)") ValueProfile profileNew,
                        @Cached("createClassProfile()") ValueProfile profileNewFactory) {
            if (arguments.length != 0 || keywords.length != 0) {
                Object type = getClassNode.execute(self);
                if (overridesNew.profile(overridesBuiltinMethod(type, profileInit, lookupInit, profileInitFactory, ObjectBuiltinsFactory.InitNodeFactory.class))) {
                    throw raise(TypeError, ErrorMessages.INIT_TAKES_ONE_ARG_OBJECT);
                }

                if (overridesInit.profile(!overridesBuiltinMethod(type, profileNew, lookupNew, profileNewFactory, BuiltinConstructorsFactory.ObjectNodeFactory.class))) {
                    throw raise(TypeError, ErrorMessages.INIT_TAKES_ONE_ARG, type);
                }
            }
            return PNone.NONE;
        }

        protected static ValueProfile createLookupProfile(Node node) {
            if (PythonLanguage.get(node).isSingleContext()) {
                return ValueProfile.createIdentityProfile();
            } else {
                return ValueProfile.createClassProfile();
            }
        }

        /**
         * Simple utility method to check if a method was overridden. The {@code profile} parameter
         * must {@emph not} be an identity profile when AST sharing is enabled.
         */
        public static <T extends NodeFactory<? extends PythonBuiltinBaseNode>> boolean overridesBuiltinMethod(Object type, ValueProfile profile, LookupCallableSlotInMRONode lookup,
                        ValueProfile factoryProfile, Class<T> builtinNodeFactoryClass) {
            Object method = profile.profile(lookup.execute(type));
            if (method instanceof PBuiltinFunction) {
                NodeFactory<? extends PythonBuiltinBaseNode> factory = factoryProfile.profile(((PBuiltinFunction) method).getBuiltinNodeFactory());
                return !builtinNodeFactoryClass.isInstance(factory);
            } else if (method instanceof PBuiltinMethod) {
                NodeFactory<? extends PythonBuiltinBaseNode> factory = factoryProfile.profile(((PBuiltinMethod) method).getFunction().getBuiltinNodeFactory());
                return !builtinNodeFactoryClass.isInstance(factory);
            } else if (method instanceof BuiltinMethodDescriptor) {
                return !((BuiltinMethodDescriptor) method).isSameFactory(builtinNodeFactoryClass);
            }
            return true;
        }
    }

    @Builtin(name = J___HASH__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class HashNode extends PythonUnaryBuiltinNode {
        @Specialization
        public int hash(PythonBuiltinClassType self) {
            return hash(getCore().lookupType(self));
        }

        @TruffleBoundary
        @Specialization(guards = "!isPythonBuiltinClassType(self)")
        public static int hash(Object self) {
            return self.hashCode();
        }
    }

    @Builtin(name = J___EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class EqNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object eq(Object self, Object other,
                        @Cached ConditionProfile isEq,
                        @Cached IsNode isNode) {
            if (isEq.profile(isNode.execute(self, other))) {
                return true;
            } else {
                // Return NotImplemented instead of False, so if two objects are compared, both get
                // a chance at the comparison
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }
    }

    @Builtin(name = J___NE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class NeNode extends PythonBinaryBuiltinNode {

        @Child private LookupAndCallBinaryNode eqNode;
        @Child private CoerceToBooleanNode ifFalseNode;

        @Specialization
        static boolean ne(PythonAbstractNativeObject self, PythonAbstractNativeObject other,
                        @Cached CExtNodes.PointerCompareNode nativeNeNode) {
            return nativeNeNode.execute(T___NE__, self, other);
        }

        @Fallback
        Object ne(VirtualFrame frame, Object self, Object other) {
            if (eqNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                eqNode = insert(LookupAndCallBinaryNode.create(SpecialMethodSlot.Eq));
            }
            Object result = eqNode.executeObject(frame, self, other);
            if (result == PNotImplemented.NOT_IMPLEMENTED) {
                return result;
            }
            if (ifFalseNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                ifFalseNode = insert(CoerceToBooleanNode.createIfFalseNode());
            }
            return ifFalseNode.executeBoolean(frame, result);
        }
    }

    @Builtin(name = J___LT__, minNumOfPositionalArgs = 2)
    @Builtin(name = J___LE__, minNumOfPositionalArgs = 2)
    @Builtin(name = J___GT__, minNumOfPositionalArgs = 2)
    @Builtin(name = J___GE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class LtLeGtGeNode extends PythonBinaryBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        static Object notImplemented(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = J___STR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class StrNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object str(VirtualFrame frame, Object self,
                        @Cached("create(Repr)") LookupAndCallUnaryNode reprNode) {
            return reprNode.executeObject(frame, self);
        }
    }

    @Builtin(name = J___REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = "isNone(self)")
        static TruffleString reprNone(@SuppressWarnings("unused") PNone self) {
            return T_NONE;
        }

        @Specialization(guards = "!isNone(self)")
        static TruffleString repr(VirtualFrame frame, Object self,
                        @Cached ObjectNodes.DefaultObjectReprNode defaultReprNode) {
            return defaultReprNode.execute(frame, self);
        }
    }

    @ImportStatic(PGuards.class)
    @Builtin(name = J___GETATTRIBUTE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class GetAttributeNode extends PythonBinaryBuiltinNode {
        @CompilationFinal private int profileFlags = 0;
        private static final int HAS_DESCR = 1;
        private static final int HAS_DATA_DESCR = 2;
        private static final int HAS_VALUE = 4;
        private static final int HAS_NO_VALUE = 8;

        @Child private LookupCallableSlotInMRONode lookupGetNode;
        @Child private LookupCallableSlotInMRONode lookupSetNode;
        @Child private LookupCallableSlotInMRONode lookupDeleteNode;
        @Child private CallTernaryMethodNode dispatchGet;
        @Child private ReadAttributeFromObjectNode attrRead;
        @Child private GetClassNode getDescClassNode;

        protected static int tsLen(TruffleString ts) {
            CompilerAsserts.neverPartOfCompilation();
            return TruffleString.CodePointLengthNode.getUncached().execute(ts, TS_ENCODING);
        }

        // Shortcut, only useful for interpreter performance, but doesn't hurt peak
        @Specialization(guards = {"keyObj == cachedKey", "cachedKeyLen < 32"}, limit = "1")
        protected Object doItTruffleString(VirtualFrame frame, Object object, @SuppressWarnings("unused") TruffleString keyObj,
                        @SuppressWarnings("unused") @Cached("keyObj") TruffleString cachedKey,
                        @SuppressWarnings("unused") @Cached("tsLen(cachedKey)") int cachedKeyLen,
                        @Shared("getClassNode") @Cached GetClassNode getClassNode,
                        @Cached("create(cachedKey)") LookupAttributeInMRONode lookup) {
            Object type = getClassNode.execute(object);
            Object descr = lookup.execute(type);
            return fullLookup(frame, object, cachedKey, type, descr);
        }

        @Specialization
        protected Object doIt(VirtualFrame frame, Object object, Object keyObj,
                        @Cached LookupAttributeInMRONode.Dynamic lookup,
                        @Shared("getClassNode") @Cached GetClassNode getClassNode,
                        @Cached CastToTruffleStringNode castKeyToStringNode) {
            TruffleString key;
            try {
                key = castKeyToStringNode.execute(keyObj);
            } catch (CannotCastException e) {
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.ATTR_NAME_MUST_BE_STRING, keyObj);
            }

            Object type = getClassNode.execute(object);
            Object descr = lookup.execute(type, key);
            return fullLookup(frame, object, key, type, descr);
        }

        private Object fullLookup(VirtualFrame frame, Object object, TruffleString key, Object type, Object descr) {
            Object dataDescClass = null;
            boolean hasDescr = descr != PNone.NO_VALUE;
            if (hasDescr && (profileFlags & HAS_DESCR) == 0) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                profileFlags |= HAS_DESCR;
            }
            if (hasDescr) {
                dataDescClass = getDescClass(descr);
                Object delete = PNone.NO_VALUE;
                Object set = lookupSet(dataDescClass);
                if (set == PNone.NO_VALUE) {
                    delete = lookupDelete(dataDescClass);
                }
                boolean hasDataDescr = set != PNone.NO_VALUE || delete != PNone.NO_VALUE;
                if (hasDataDescr && (profileFlags & HAS_DATA_DESCR) == 0) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    profileFlags |= HAS_DATA_DESCR;
                }
                if (hasDataDescr) {
                    Object get = lookupGet(dataDescClass);
                    if (PGuards.isCallableOrDescriptor(get)) {
                        // Only override if __get__ is defined, too, for compatibility with CPython.
                        return dispatch(frame, object, type, descr, get);
                    }
                }
            }
            Object value = readAttribute(object, key);
            boolean hasValue = value != PNone.NO_VALUE;
            if (hasValue && (profileFlags & HAS_VALUE) == 0) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                profileFlags |= HAS_VALUE;
            }
            if (hasValue) {
                return value;
            }
            if ((profileFlags & HAS_NO_VALUE) == 0) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                profileFlags |= HAS_NO_VALUE;
            }
            if (hasDescr) {
                if (object == PNone.NONE) {
                    if (descr instanceof PBuiltinFunction) {
                        // Special case for None object. We cannot call function.__get__(None,
                        // type(None)),
                        // because that would return an unbound method
                        return factory().createBuiltinMethod(PNone.NONE, (PBuiltinFunction) descr);
                    }
                }
                Object get = lookupGet(dataDescClass);
                if (get == PNone.NO_VALUE) {
                    return descr;
                } else if (PGuards.isCallableOrDescriptor(get)) {
                    return dispatch(frame, object, type, descr, get);
                }
            }
            throw raise(AttributeError, ErrorMessages.OBJ_P_HAS_NO_ATTR_S, object, key);
        }

        private Object readAttribute(Object object, Object key) {
            if (attrRead == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                attrRead = insert(ReadAttributeFromObjectNode.create());
            }
            return attrRead.execute(object, key);
        }

        private Object dispatch(VirtualFrame frame, Object object, Object type, Object descr, Object get) {
            if (dispatchGet == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                dispatchGet = insert(CallTernaryMethodNode.create());
            }
            return dispatchGet.execute(frame, get, descr, object, type);
        }

        private Object getDescClass(Object desc) {
            if (getDescClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getDescClassNode = insert(GetClassNode.create());
            }
            return getDescClassNode.execute(desc);
        }

        private Object lookupGet(Object dataDescClass) {
            if (lookupGetNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupGetNode = insert(LookupCallableSlotInMRONode.create(SpecialMethodSlot.Get));
            }
            return lookupGetNode.execute(dataDescClass);
        }

        private Object lookupDelete(Object dataDescClass) {
            if (lookupDeleteNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupDeleteNode = insert(LookupCallableSlotInMRONode.create(SpecialMethodSlot.Delete));
            }
            return lookupDeleteNode.execute(dataDescClass);
        }

        private Object lookupSet(Object dataDescClass) {
            if (lookupSetNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupSetNode = insert(LookupCallableSlotInMRONode.create(SpecialMethodSlot.Set));
            }
            return lookupSetNode.execute(dataDescClass);
        }

        public static GetAttributeNode create() {
            return GetAttributeNodeFactory.create();
        }
    }

    @Builtin(name = J___SETATTR__, minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class SetattrNode extends ObjectNodes.AbstractSetattrNode {
        @Child WriteAttributeToObjectNode writeNode;

        @Override
        protected boolean writeAttribute(Object object, TruffleString key, Object value) {
            if (writeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                writeNode = insert(WriteAttributeToObjectNode.create());
            }
            return writeNode.execute(object, key, value);
        }

        public static SetattrNode create() {
            return ObjectBuiltinsFactory.SetattrNodeFactory.create();
        }
    }

    @Builtin(name = J___DELATTR__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class DelattrNode extends PythonBinaryBuiltinNode {
        @Child private GetClassNode getDescClassNode;

        @Specialization
        protected PNone doIt(VirtualFrame frame, Object object, Object keyObj,
                        @Cached LookupAttributeInMRONode.Dynamic getExisting,
                        @Cached GetClassNode getClassNode,
                        @Cached("create(T___DELETE__)") LookupAttributeInMRONode lookupDeleteNode,
                        @Cached CallBinaryMethodNode callSetNode,
                        @Cached ReadAttributeFromObjectNode attrRead,
                        @Cached WriteAttributeToObjectNode writeNode,
                        @Cached CastToTruffleStringNode castKeyToStringNode) {
            TruffleString key;
            try {
                key = castKeyToStringNode.execute(keyObj);
            } catch (CannotCastException e) {
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.ATTR_NAME_MUST_BE_STRING, keyObj);
            }

            Object type = getClassNode.execute(object);
            Object descr = getExisting.execute(type, key);
            if (descr != PNone.NO_VALUE) {
                Object dataDescClass = getDescClass(descr);
                Object set = lookupDeleteNode.execute(dataDescClass);
                if (PGuards.isCallable(set)) {
                    callSetNode.executeObject(frame, set, descr, object);
                    return PNone.NONE;
                }
            }
            Object currentValue = attrRead.execute(object, key);
            if (currentValue != PNone.NO_VALUE) {
                if (writeNode.execute(object, key, PNone.NO_VALUE)) {
                    return PNone.NONE;
                }
            }
            if (descr != PNone.NO_VALUE) {
                throw raise(AttributeError, ErrorMessages.ATTR_S_READONLY, key);
            } else {
                throw raise(AttributeError, ErrorMessages.OBJ_P_HAS_NO_ATTR_S, object, key);
            }
        }

        private Object getDescClass(Object desc) {
            if (getDescClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getDescClassNode = insert(GetClassNode.create());
            }
            return getDescClassNode.execute(desc);
        }
    }

    @Builtin(name = J___DICT__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    public abstract static class DictNode extends PythonBinaryBuiltinNode {
        @Child private IsBuiltinClassProfile exactObjInstanceProfile = IsBuiltinClassProfile.create();
        @Child private IsBuiltinClassProfile exactBuiltinInstanceProfile = IsBuiltinClassProfile.create();

        protected boolean isExactObjectInstance(PythonObject self) {
            return exactObjInstanceProfile.profileObject(self, PythonBuiltinClassType.PythonObject);
        }

        protected boolean isBuiltinObjectExact(PythonObject self) {
            // any builtin class except Modules
            return exactBuiltinInstanceProfile.profileIsOtherBuiltinObject(self, PythonBuiltinClassType.PythonModule);
        }

        @Specialization(guards = {"!isBuiltinObjectExact(self)", "!isExactObjectInstance(self)", "isNoValue(none)"})
        Object dict(VirtualFrame frame, PythonObject self, @SuppressWarnings("unused") PNone none,
                        @Cached GetClassNode getClassNode,
                        @Cached GetBaseClassNode getBaseNode,
                        @Cached("createForLookupOfUnmanagedClasses(T___DICT__)") LookupAttributeInMRONode getDescrNode,
                        @Cached DescrGetNode getNode,
                        @Cached GetOrCreateDictNode getDict,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") InteropLibrary iLib,
                        @Cached BranchProfile branchProfile) {
            // typeobject.c#subtype_getdict()
            Object func = getDescrFromBuiltinBase(getClassNode.execute(self), getBaseNode, getDescrNode);
            if (func != null) {
                branchProfile.enter();
                return getNode.execute(frame, func, self);
            }

            return getDict.execute(self);
        }

        @Specialization(guards = {"!isBuiltinObjectExact(self)", "!isExactObjectInstance(self)", "!isPythonModule(self)"})
        static Object dict(VirtualFrame frame, PythonObject self, PDict dict,
                        @Cached GetClassNode getClassNode,
                        @Cached GetBaseClassNode getBaseNode,
                        @Cached("createForLookupOfUnmanagedClasses(T___DICT__)") LookupAttributeInMRONode getDescrNode,
                        @Cached DescrSetNode setNode,
                        @Cached SetDictNode setDict,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") InteropLibrary iLib,
                        @Cached BranchProfile branchProfile) {
            // typeobject.c#subtype_setdict()
            Object func = getDescrFromBuiltinBase(getClassNode.execute(self), getBaseNode, getDescrNode);
            if (func != null) {
                branchProfile.enter();
                return setNode.execute(frame, func, self, dict);
            }

            setDict.execute(self, dict);
            return PNone.NONE;
        }

        @Specialization(guards = "isNoValue(none)")
        Object dict(PythonAbstractNativeObject self, @SuppressWarnings("unused") PNone none,
                        @Cached GetDictIfExistsNode getDict) {
            PDict dict = getDict.execute(self);
            if (dict == null) {
                raise(self, none);
            }
            return dict;
        }

        @Specialization
        static Object dict(VirtualFrame frame, @SuppressWarnings("unused") PythonObject self, @SuppressWarnings("unused") DescriptorDeleteMarker marker,
                        @Cached GetClassNode getClassNode,
                        @Cached GetBaseClassNode getBaseNode,
                        @Cached("createForLookupOfUnmanagedClasses(T___DICT__)") LookupAttributeInMRONode getDescrNode,
                        @Cached DescrDeleteNode deleteNode,
                        @Cached DeleteDictNode deleteDictNode,
                        @Cached BranchProfile branchProfile) {
            // typeobject.c#subtype_setdict()
            Object func = getDescrFromBuiltinBase(getClassNode.execute(self), getBaseNode, getDescrNode);
            if (func != null) {
                branchProfile.enter();
                return deleteNode.execute(frame, func, self);
            }
            deleteDictNode.execute(self);
            return PNone.NONE;
        }

        /**
         * see typeobject.c#get_builtin_base_with_dict()
         */
        private static Object getDescrFromBuiltinBase(Object type, GetBaseClassNode getBaseNode, LookupAttributeInMRONode getDescrNode) {
            Object t = type;
            Object base = getBaseNode.execute(t);
            while (base != null) {
                if (t instanceof PythonBuiltinClass) {
                    Object func = getDescrNode.execute(t);
                    if (func != PNone.NO_VALUE) {
                        return func;
                    }
                }
                t = base;
                base = getBaseNode.execute(t);
            }
            return null;
        }

        @Specialization(guards = {"!isNoValue(mapping)", "!isDict(mapping)", "!isDeleteMarker(mapping)"})
        Object dict(@SuppressWarnings("unused") Object self, Object mapping) {
            throw raise(TypeError, ErrorMessages.DICT_MUST_BE_SET_TO_DICT, mapping);
        }

        @Fallback
        Object raise(Object self, @SuppressWarnings("unused") Object dict) {
            throw raise(AttributeError, ErrorMessages.OBJ_P_HAS_NO_ATTR_S, self, "__dict__");
        }

    }

    @Builtin(name = J___FORMAT__, minNumOfPositionalArgs = 2, parameterNames = {"$self", "format_spec"})
    @ArgumentClinic(name = "format_spec", conversion = ClinicConversion.TString)
    @GenerateNodeFactory
    abstract static class FormatNode extends PythonBinaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return FormatNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = "!formatString.isEmpty()")
        Object format(Object self, @SuppressWarnings("unused") TruffleString formatString) {
            throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.UNSUPPORTED_FORMAT_STRING_PASSED_TO_P_FORMAT, self);
        }

        @Specialization(guards = "formatString.isEmpty()")
        static Object format(VirtualFrame frame, Object self, @SuppressWarnings("unused") TruffleString formatString,
                        @Cached("create(Str)") LookupAndCallUnaryNode strCall) {
            return strCall.executeObject(frame, self);
        }
    }

    @Builtin(name = J_RICHCMP, minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class RichCompareNode extends PythonTernaryBuiltinNode {
        protected static final int NO_SLOW_PATH = Integer.MAX_VALUE;
        @CompilationFinal private boolean seenNonBoolean = false;

        static BinaryComparisonNode createOp(TruffleString op) {
            switch (op.toJavaStringUncached()) {
                case "<":
                    return BinaryComparisonNodeFactory.LtNodeGen.create();
                case ">":
                    return BinaryComparisonNodeFactory.GtNodeGen.create();
                case "==":
                    return BinaryComparisonNodeFactory.EqNodeGen.create();
                case ">=":
                    return BinaryComparisonNodeFactory.GeNodeGen.create();
                case "<=":
                    return BinaryComparisonNodeFactory.LeNodeGen.create();
                case "<>":
                case "!=":
                    return BinaryComparisonNodeFactory.NeNodeGen.create();
            }
            throw new RuntimeException("unexpected operation: " + op);
        }

        @Specialization(guards = "stringEquals(op, cachedOp, equalNode)", limit = "NO_SLOW_PATH")
        boolean richcmp(VirtualFrame frame, Object left, Object right, @SuppressWarnings("unused") TruffleString op,
                        @SuppressWarnings("unused") @Cached TruffleString.EqualNode equalNode,
                        @SuppressWarnings("unused") @Cached("op") TruffleString cachedOp,
                        @Cached("createOp(op)") BinaryComparisonNode node,
                        @Cached("createIfTrueNode()") CoerceToBooleanNode castToBooleanNode) {
            if (!seenNonBoolean) {
                try {
                    return node.executeBool(frame, left, right);
                } catch (UnexpectedResultException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    seenNonBoolean = true;
                    return castToBooleanNode.executeBoolean(frame, e.getResult());
                }
            } else {
                return castToBooleanNode.executeBoolean(frame, node.executeObject(frame, left, right));
            }
        }
    }

    @Builtin(name = J___INIT_SUBCLASS__, minNumOfPositionalArgs = 1, isClassmethod = true)
    @GenerateNodeFactory
    abstract static class InitSubclass extends PythonUnaryBuiltinNode {
        @Specialization
        static PNone initSubclass(@SuppressWarnings("unused") Object self) {
            return PNone.NONE;
        }
    }

    @Builtin(name = J___SUBCLASSHOOK__, minNumOfPositionalArgs = 1, declaresExplicitSelf = true, takesVarArgs = true, takesVarKeywordArgs = true, isClassmethod = true)
    @GenerateNodeFactory
    abstract static class SubclassHookNode extends PythonVarargsBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        static Object notImplemented(Object self, Object[] arguments, PKeyword[] keywords) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        @Override
        public Object varArgExecute(VirtualFrame frame, Object self, Object[] arguments, PKeyword[] keywords) throws VarargsBuiltinDirectInvocationNotSupported {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = J___SIZEOF__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class SizeOfNode extends PythonUnaryBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        static Object doit(VirtualFrame frame, Object obj,
                        @Cached GetClassNode getClassNode,
                        @Cached PyLongAsLongNode asLongNode,
                        @Cached PyObjectSizeNode sizeNode,
                        @Cached PyObjectLookupAttr lookupAttr,
                        @Cached PyObjectGetAttr getAttr) {
            Object cls = getClassNode.execute(obj);
            long size = 0;
            Object itemsize = lookupAttr.execute(frame, obj, T___ITEMSIZE__);
            if (itemsize != PNone.NO_VALUE) {
                Object clsItemsize = lookupAttr.execute(frame, cls, T___ITEMSIZE__);
                Object objLen = lookupAttr.execute(frame, obj, T___LEN__);
                if (clsItemsize != PNone.NO_VALUE && objLen != PNone.NO_VALUE) {
                    size = asLongNode.execute(frame, clsItemsize) * sizeNode.execute(frame, obj);
                }
            }
            size += asLongNode.execute(frame, getAttr.execute(frame, cls, T___BASICSIZE__));
            return size;
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    // Note: this must not inherit from PythonUnaryBuiltinNode, i.e. must not be AST inlined.
    // The CommonReduceNode seems to need a fresh frame, otherwise it can mess up the existing one.
    public abstract static class ReduceNode extends PythonBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        static Object doit(VirtualFrame frame, Object obj, @SuppressWarnings("unused") Object ignored,
                        @Cached ObjectNodes.CommonReduceNode commonReduceNode) {
            return commonReduceNode.execute(frame, obj, 0);
        }
    }

    @Builtin(name = J___REDUCE_EX__, minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "protocol"})
    @ArgumentClinic(name = "protocol", conversion = ArgumentClinic.ClinicConversion.Int)
    @GenerateNodeFactory
    // Note: this must not inherit from PythonBinaryClinicBuiltinNode, i.e. must not be AST inlined.
    // The CommonReduceNode seems to need a fresh frame, otherwise it can mess up the existing one.
    public abstract static class ReduceExNode extends PythonClinicBuiltinNode {
        static final Object REDUCE_FACTORY = ObjectBuiltinsFactory.ReduceNodeFactory.getInstance();

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ReduceExNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        @SuppressWarnings("unused")
        static Object doit(VirtualFrame frame, Object obj, int proto,
                        @Cached PyObjectLookupAttr lookupAttr,
                        @Cached CallNode callNode,
                        @Cached ConditionProfile reduceProfile,
                        @Cached ObjectNodes.CommonReduceNode commonReduceNode) {
            Object _reduce = lookupAttr.execute(frame, obj, T___REDUCE__);
            if (reduceProfile.profile(_reduce != PNone.NO_VALUE)) {
                // Check if __reduce__ has been overridden:
                // "type(obj).__reduce__ is not object.__reduce__"
                if (!(_reduce instanceof PBuiltinMethod) || ((PBuiltinMethod) _reduce).getFunction().getBuiltinNodeFactory() != REDUCE_FACTORY) {
                    return callNode.execute(frame, _reduce);
                }
            }
            return commonReduceNode.execute(frame, obj, proto);
        }
    }

    @Builtin(name = J___DIR__, minNumOfPositionalArgs = 1, doc = "__dir__ for generic objects\n\n\tReturns __dict__, __class__ and recursively up the\n\t__class__.__bases__ chain.")
    @GenerateNodeFactory
    public abstract static class DirNode extends PythonBuiltinNode {
        @Specialization
        Object dir(VirtualFrame frame, Object obj,
                        @Cached PyObjectLookupAttr lookupAttrNode,
                        @Cached CallNode callNode,
                        @Cached GetClassNode getClassNode,
                        @Cached IsSubtypeNode isSubtypeNode,
                        @Cached com.oracle.graal.python.builtins.objects.type.TypeBuiltins.DirNode dirNode) {
            PSet names = factory().createSet();
            Object updateCallable = lookupAttrNode.execute(frame, names, T_UPDATE);
            Object ns = lookupAttrNode.execute(frame, obj, T___DICT__);
            if (isSubtypeNode.execute(frame, getClassNode.execute(ns), PythonBuiltinClassType.PDict)) {
                callNode.execute(frame, updateCallable, ns);
            }
            Object klass = lookupAttrNode.execute(frame, obj, T___CLASS__);
            if (klass != PNone.NO_VALUE) {
                callNode.execute(frame, updateCallable, dirNode.execute(frame, klass));
            }
            return names;
        }
    }

}
