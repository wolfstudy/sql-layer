/**
 * Copyright (C) 2011 Akiban Technologies Inc.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses.
 */

package com.akiban.server.t3expressions;

import com.akiban.server.error.NoSuchFunctionException;
import com.akiban.server.error.WrongExpressionArityException;
import com.akiban.server.types3.LazyList;
import com.akiban.server.types3.TBundleID;
import com.akiban.server.types3.TCast;
import com.akiban.server.types3.TCastBase;
import com.akiban.server.types3.TClass;
import com.akiban.server.types3.TExecutionContext;
import com.akiban.server.types3.TInstance;
import com.akiban.server.types3.TOverload;
import com.akiban.server.types3.TOverloadResult;
import com.akiban.server.types3.TPreptimeContext;
import com.akiban.server.types3.TPreptimeValue;
import com.akiban.server.types3.common.types.NoAttrTClass;
import com.akiban.server.types3.pvalue.PUnderlying;
import com.akiban.server.types3.pvalue.PValueSource;
import com.akiban.server.types3.pvalue.PValueTarget;
import com.akiban.server.types3.texpressions.Constantness;
import com.akiban.server.types3.texpressions.TInputSetBuilder;
import com.akiban.server.types3.texpressions.TOverloadBase;
import com.akiban.server.types3.texpressions.TValidatedOverload;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

public class OverloadResolverTest {
    private static class SimpleRegistry implements T3ScalarsRegistry {
        private final Map<String,List<TValidatedOverload>> validatedMap = new HashMap<String, List<TValidatedOverload>>();
        private final Map<TOverload,TValidatedOverload> originalMap = new HashMap<TOverload, TValidatedOverload>();
        private final Map<TClass, Map<TClass, TCast>> castMap = new HashMap<TClass, Map<TClass, TCast>>();


        public SimpleRegistry(TOverload... overloads) {
            for(TOverload overload : overloads) {
                TValidatedOverload validated = new TValidatedOverload(overload);
                originalMap.put(overload, validated);
                List<TValidatedOverload> list = validatedMap.get(overload.overloadName());
                if(list == null) {
                    list = new ArrayList<TValidatedOverload>();
                    validatedMap.put(overload.overloadName(), list);
                }
                list.add(validated);
            }
        }

        public void setCasts(TCast... casts) {
            castMap.clear();
            for(TCast cast : casts) {
                Map<TClass,TCast> map = castMap.get(cast.sourceClass());
                if(map == null) {
                    map = new HashMap<TClass, TCast>();
                    castMap.put(cast.sourceClass(), map);
                }
                map.put(cast.targetClass(), cast);
            }
        }

        @Override
        public List<TValidatedOverload> getOverloads(String name) {
            return validatedMap.get(name);
        }

        @Override
        public OverloadResolutionResult get(String name, List<? extends TClass> inputClasses) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TCast cast(TClass source, TClass target) {
            Map<TClass,TCast> map = castMap.get(source);
            if(map != null) {
                return map.get(target);
            }
            return null;
        }

        @Override
        public TClassPossibility commonTClass(TClass one, TClass two) {
            return T3ScalarsRegistry.NO_COMMON;
        }

        public TValidatedOverload validated(TOverload overload) {
            return originalMap.get(overload);
        }
    }

    private static class TestClassBase extends NoAttrTClass {
        private static final TBundleID TEST_BUNDLE_ID = new TBundleID("test", new UUID(0,0));

        public TestClassBase(String name, PUnderlying pUnderlying) {
            super(TEST_BUNDLE_ID, name, 1, 1, 1, pUnderlying, null);
        }
    }

    private static class TestCastBase extends TCastBase {
        public TestCastBase(TClass sourceAndTarget) {
            this(sourceAndTarget, sourceAndTarget, true);
        }

        public TestCastBase(TClass source, TClass target, boolean isAutomatic) {
            super(source, target, isAutomatic, Constantness.UNKNOWN);
        }

        @Override
        public TInstance targetInstance(TPreptimeContext context, TPreptimeValue preptimeInput, TInstance specifiedTarget) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void evaluate(TExecutionContext context, PValueSource source, PValueTarget target) {
            throw new UnsupportedOperationException();
        }
    }


    private static final String MUL_NAME = "*";
    private static class TestMulBase extends TOverloadBase {
        private final TClass tLeft;
        private final TClass tRight;
        private final TClass tTarget;

        public TestMulBase(TClass tClass) {
            this(tClass, tClass, tClass);
        }

        public TestMulBase(TClass tLeft, TClass tRight, TClass tTarget) {
            this.tLeft = tLeft;
            this.tRight = tRight;
            this.tTarget = tTarget;
        }

        @Override
        protected void buildInputSets(TInputSetBuilder builder) {
            if (tLeft == tRight) {
                builder.covers(tLeft, 0, 1);
            } else {
                builder.covers(tLeft, 0);
                builder.covers(tRight, 1);
            }
        }

        @Override
        protected void doEvaluate(TExecutionContext context, LazyList<? extends PValueSource> inputs, PValueTarget output) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String overloadName() {
            return MUL_NAME;
        }

        @Override
        public TOverloadResult resultType() {
            return TOverloadResult.fixed(tTarget.instance());
        }
    }

    private static class TestGetBase extends TOverloadBase {
        private final String name;
        private final TClass tResult;
        private final TInputSetBuilder builder = new TInputSetBuilder();

        public TestGetBase(String name, TClass tResult) {
            this.name = name;
            this.tResult = tResult;
        }

        public TInputSetBuilder builder() {
            return builder;
        }

        @Override
        protected void buildInputSets(TInputSetBuilder builder) {
            builder.reset(this.builder);
        }

        @Override
        protected void doEvaluate(TExecutionContext context, LazyList<? extends PValueSource> inputs, PValueTarget output) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String overloadName() {
            return name;
        }

        @Override
        public TOverloadResult resultType() {
            return (tResult == null) ? TOverloadResult.picking() : TOverloadResult.fixed(tResult.instance());
        }
    }

    private final static TClass TINT = new TestClassBase("int", PUnderlying.INT_32);
    private final static TClass TBIGINT = new TestClassBase("bigint", PUnderlying.INT_64);
    private final static TClass TDATE = new TestClassBase("date",  PUnderlying.DOUBLE);
    private final static TClass TVARCHAR = new TestClassBase("varchar",  PUnderlying.BYTES);

    private final static TCast C_INT_INT = new TestCastBase(TINT);
    private final static TCast C_INT_BIGINT = new TestCastBase(TINT, TBIGINT, true);
    private final static TCast C_BIGINT_BIGINT = new TestCastBase(TBIGINT);
    private final static TCast C_BIGINT_INT = new TestCastBase(TBIGINT, TINT, false);
    private final static TCast C_DATE_DATE = new TestCastBase(TDATE, TDATE, true);

    private final static TestMulBase MUL_INTS = new TestMulBase(TINT);
    private final static TestMulBase MUL_BIGINTS = new TestMulBase(TBIGINT);
    private final static TestMulBase MUL_DATE_INT = new TestMulBase(TDATE, TINT, TDATE);


    private SimpleRegistry registry;
    private OverloadResolver resolver;

    private void init(TOverload... overloads) {
        registry = new SimpleRegistry(overloads);
        resolver = new OverloadResolver(registry);
    }


    private static TPreptimeValue prepVal(TClass tClass) {
        return (tClass != null) ? new TPreptimeValue(tClass.instance()) : new TPreptimeValue();
    }

    private static List<TPreptimeValue> prepVals(TClass... tClasses) {
        TPreptimeValue[] prepVals = new TPreptimeValue[tClasses.length];
        for(int i = 0; i < tClasses.length; ++i) {
            prepVals[i] = prepVal(tClasses[i]);
        }
        return Arrays.asList(prepVals);
    }

    private void checkResolved(String msg, TOverload expected, String overloadName, List<TPreptimeValue> prepValues) {
        TValidatedOverload validated = expected != null ? registry.validated(expected) : null;
        // result.getPickingClass() not checked, SimpleRegistry doesn't implement commonTypes()
        OverloadResolver.OverloadResult result = resolver.get(overloadName, prepValues);
        assertSame(msg, validated, result != null ? result.getOverload() : null);
    }


    @Test(expected=NoSuchFunctionException.class)
    public void noSuchOverload() {
        init();
        resolver.get("foo", Arrays.asList(prepVal(TINT)));
    }

    @Test(expected=WrongExpressionArityException.class)
    public void knownOverloadTooFewParams() {
        init(MUL_INTS);
        resolver.get(MUL_NAME, prepVals(TINT));
    }

    @Test(expected=WrongExpressionArityException.class)
    public void knownOverloadTooManyParams() {
        init(MUL_INTS);
        resolver.get(MUL_NAME, prepVals(TINT, TINT, TINT));
    }

    // default resolution, exact match
    @Test
    public void mulIntWithInts() {
        init(MUL_INTS);
        checkResolved("INT*INT", MUL_INTS, MUL_NAME, prepVals(TINT, TINT));
    }

    // default resolution, types don't match (no registered casts, int -> bigint)
    @Test
    public void mulBigIntWithInts() {
        init(MUL_BIGINTS);
        checkResolved("INT*INT", MUL_BIGINTS, MUL_NAME, prepVals(TINT, TINT));
    }

    // default resolution, requires weak cast (bigint -> int)
    @Test
    public void mulIntWithBigInts() {
        init(MUL_INTS);
        checkResolved("INT*INT", MUL_INTS, MUL_NAME, prepVals(TINT, TINT));
    }

    // input resolution, no casts
    @Test
    public void mulIntMulBigIntWithIntsNoCasts() {
        init(MUL_INTS, MUL_BIGINTS);
        checkResolved("INT*INT", null, MUL_NAME, prepVals(TINT, TINT));
    }

    // input resolution, type only casts, only one candidate
    @Test
    public void mulIntMulBigIntWithIntsTypeOnlyCasts() {
        init(MUL_INTS, MUL_BIGINTS);
        registry.setCasts(C_INT_INT, C_BIGINT_BIGINT);
        checkResolved("INT*INT", MUL_INTS, MUL_NAME, prepVals(TINT, TINT));
        checkResolved("BIGINT*BIGINT", MUL_BIGINTS, MUL_NAME, prepVals(TBIGINT, TBIGINT));
    }

    // input resolution, more casts, 2 candidates but 1 more specific
    @Test
    public void mulIntMulBigIntWithIntsAndIntBigintStrongAndWeakCasts() {
        init(MUL_INTS, MUL_BIGINTS);
        registry.setCasts(C_INT_INT, C_INT_BIGINT,
                          C_BIGINT_BIGINT, C_BIGINT_INT);
        // 2 candidates, 1 more specific
        checkResolved("INT*INT", MUL_INTS, MUL_NAME, prepVals(TINT, TINT));
    }

    @Test
    public void specMulExampleIntBigIntAndDateCombos() {
        init(MUL_INTS, MUL_BIGINTS, MUL_DATE_INT);
        registry.setCasts(C_INT_INT, C_INT_BIGINT,
                          C_BIGINT_BIGINT, C_BIGINT_INT,
                          C_DATE_DATE);
        // 2 survive filtering, 1 more specific
        checkResolved("INT*INT", MUL_INTS, MUL_NAME, prepVals(TINT, TINT));
        // 1 survives filtering (bigint can't be strongly cast to INT or DATE)
        checkResolved("BIGINT*BIGINT", MUL_BIGINTS, MUL_NAME, prepVals(TBIGINT, TBIGINT));
        // 1 survives filtering (INT strongly cast to BIGINT)
        checkResolved("BIGINT*INT", MUL_BIGINTS, MUL_NAME, prepVals(TBIGINT, TINT));
        // 1 survives filtering
        checkResolved("DATE*INT", MUL_DATE_INT, MUL_NAME, prepVals(TDATE, TINT));
        // 3 survive filtering, 1 less specific, 2 candidates
        checkResolved("?*INT", null, MUL_NAME, prepVals(null, TINT));
    }

    @Test
    public void conflictingOverloads() {
        final String NAME = "foo";
        // Overloads aren't valid and should(?) be rejected by real registry,
        // but make sure resolver doesn't choke
        TestGetBase posPos = new TestGetBase(NAME, TINT);
        posPos.builder().covers(TINT, 0, 1);
        TestGetBase posRem = new TestGetBase(NAME, TINT);
        posRem.builder().covers(TINT, 0).vararg(TINT);

        init(posPos, posRem);
        registry.setCasts(C_INT_INT);

        checkResolved(NAME+"(INT,INT)", null, NAME, prepVals(TINT, TINT));
    }

    @Test
    public void noArg() {
        final String NAME = "foo";
        TestGetBase noArg = new TestGetBase(NAME, TINT);
        init(noArg);
        checkResolved(NAME+"()", noArg, NAME, prepVals());
    }

    @Test
    public void onePosAndRemainingWithPickingSet() {
        final String NAME = "coalesce";
        TestGetBase coalesce = new TestGetBase(NAME, TVARCHAR);
        coalesce.builder().covers(null, 0).pickingVararg(null);
        init(coalesce);

        try {
            OverloadResolver.OverloadResult result = resolver.get(NAME, prepVals());
            fail("WrongArity expected but got: " + result);
        } catch(WrongExpressionArityException e) {
            // Expected
        }

        checkResolved(NAME+"(INT)", coalesce, NAME, prepVals(TINT));
        checkResolved(NAME+"(INT,BIGINT)", coalesce, NAME, prepVals(TINT, TBIGINT));
        checkResolved(NAME+"(null,DATE,INT)", coalesce, NAME, prepVals(null, TDATE, TINT));
    }

    @Test
    public void onlyPickingRemaining() {
        final String NAME = "first";
        TestGetBase first = new TestGetBase(NAME, null);
        first.builder.pickingVararg(null);
        init(first);
        checkResolved(NAME+"()", first, NAME, prepVals());
        checkResolved(NAME+"(INT)", first, NAME, prepVals(TINT));
        checkResolved(NAME+"(null)", first, NAME, Arrays.asList(prepVal(null)));
        checkResolved(NAME+"(BIGINT,DATE)", first, NAME, prepVals(TBIGINT,TDATE));
    }
}
