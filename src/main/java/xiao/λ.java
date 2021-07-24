package xiao;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.util.*;
import java.util.function.Function;

import static xiao.λ.Compiler.compile1;
import static xiao.λ.FFI.*;
import static xiao.λ.FFI.listify;
import static xiao.λ.Names.*;
import static xiao.λ.Parser.parse;
import static xiao.λ.Primitives.*;

/**
 * λ
 * Lambda Calculus Compiler<br>
 *
 * 代码分为几块:<br>
 *      Parser : 把代码 (语法用 json array 来表达 s-expr) 转换成 java list 的 s-expr<br>
 *      Compiler + AST : Compiler 这里做了 desugar 的工作,<br>
 *          把表层语言(scheme 子集, 语法参见注释) 编译成 core language (pure lambda) 并消除 free variable<br>
 *      Interpreter + Value : 把 AST 解释成值<br>
 *      FFI : 把 Value 转换成宿主语言的值, 这里是把 Closure 转换成 java value<br>
 *      PrettyPrinter : 也可以当成 CodeGen 用，雾<br>
 * 大致流程： json-s-expr -> pure-lambda-s-expr -> closure<br>
 * <br>
 * ## type<br>
 *  Sym      = String<br>
 *  Env      = Sym -> Val<br>
 *  Val      = Closure<br>
 *  Closure  = Abs * Env<br>
 *  <br>
 *  S-Expr   = List | Map | String | Integer<br>
 *  Parser   = String -> S-Expr<br>
 *  Expr     = Sym | Abs | App<br>
 *  Compiler = S-Expr -> Expr<br>
 *  Apply    = Val * Val -> Val<br>
 *  Eval     = Expr * Env -> Val<br>
 * <br>
 * <br>
 *  ## 缩写 & 一些名词解释：<br>
 *  Sym : Variable Reference, Term  (term > sym > var)<br>
 *  Abs : Abstraction, Lambda, Anonymous Functions<br>
 *  App : Application, Call<br>
 *  bound var<br>
 *  unbound var (free var)<br>
 *  open term : term with unbound var<br>
 *      a closure closes an open term<br>
 *      λa.ab<br>
 *       a of ab: bound var<br>
 *       b of ab: unbound var<br>
 *
 *
 * # scheme 子集语法：<br>
 * <br>
 *   <exp> ::= <var><br>
 * <br>
 *          |  #t<br>
 *          |  #f<br>
 *          |  (if  <exp> <exp> <exp>)<br>
 *          |  (and <exp> <exp>)<br>
 *          |  (or  <exp> <exp>)<br>
 *<br>
 *          |  <nat><br>
 *          |  (zero? <exp>)<br>
 *          |  (- <exp> <exp>)<br>
 *          |  (= <exp> <exp>)<br>
 *          |  (+ <exp> <exp>)<br>
 *          |  (* <exp> <exp>)<br>
 *<br>
 *          |  <lam><br>
 *          |  (let ((<var> <exp>) ...) <exp>)<br>
 *          |  (letrec ((<var> <lam>)) <exp>)<br>
 *<br>
 *          |  (cons <exp> <exp>)<br>
 *          |  (car  <exp>)<br>
 *          |  (cdr  <exp>)<br>
 *          |  (pair? <exp>)<br>
 *          |  (null? <exp>)<br>
 *          |  '()<br>
 *<br>
 *          |  (<exp> <exp> ...)<br>
 *<br>
 *   <lam> ::= (λ (<var> ...) <exp>)<br>
 *
 * @author chuxiaofeng
 */
@SuppressWarnings("NonAsciiCharacters")
public interface λ {

    // 之所以eval不需要带环境是因为, compiler 已经把需要引用环境的 free var 全部替换掉了
    // expr 表达式树中都为 closed term

    static Expr             compile(String s)           { return compile(s, compilerEnv()); }
    static Expr             compile(String s, Env<Expr> env) { return compiler().eval(parse(s), env); }
    static Closure          eval(Expr s)                { return eval(s, new Env<>(null)); }
    static Closure          eval(Expr s, Env<Val> env)  { return ((Closure) interpreter().eval(s, env)); }
    static F                compile(Expr s)             { return compile(s, new Env<>(null)); }
    static F                compile(Expr s, Env<F> env) { return ffiCompiler().eval(s, env); }
    static int              natify(F churchNumeral)     { return natifier().eval(churchNumeral, null); }
    static boolean          boolify(F churchBoolean)    { return boolifier().eval(churchBoolean, null); }
    static Pair<Integer>    natListify (F churchList)   { return natListifier().eval(churchList, null); }
    static Pair<Boolean>    boolListify(F churchList)   { return boolListifier().eval(churchList, null); }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // 声明 Eval 和 👇这部分没啥用东西主要用来展示 Eval<From, To> 类型的

    static Eval<Object, Expr>           compiler()      { return Compiler.self; }
    static Eval<Expr,   Expr>           expander()      { return Compiler.Expander.self; }
    static Eval<Expr,   Val>            interpreter()   { return Interp.self; }
    static Eval<Expr,   F>              ffiCompiler()   { return FFI.Compiler.self; }
    static Eval<F,      Integer>        natifier()      { return natifier; }
    static Eval<F,      Boolean>        boolifier()     { return boolifier; }
    static Eval<F,      Pair<Integer>>  natListifier()  { return natListify; }
    static Eval<F,      Pair<Boolean>>  boolListifier() { return boolListify; }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    static Env<Expr> compilerEnv() {
        return bootEnv(expander());
    }

    static Env<Val> interpEnv() {
        return bootEnv(interpreter());
    }

    static <V> Env<V> bootEnv(Eval<Expr, V> eval) {
        Map<String, String> primitives = new LinkedHashMap<>();
        primitives.put(VOID, S_VOID);

        // Booleans
        primitives.put(TRUE, S_TRUE);
        primitives.put(FALSE, S_FALSE);

        // Numeral
        primitives.put(IS_ZERO, S_IS_ZERO);
        primitives.put(SUM, S_SUM);
        primitives.put(MUL, S_MUL);

        primitives.put(SUB, S_SUB);
        primitives.put(EQ, S_EQ);

        // Lists
        primitives.put(CONS, S_CONS);
        primitives.put(CAR, S_CAR);
        primitives.put(CDR, S_CDR);
        primitives.put(IS_PAIR, S_IS_PAIR);
        primitives.put(IS_NULL, S_IS_NULL);

        Env<V> env = new Env<>(null);

        primitives.forEach((n, s) -> env.put(Sym.of(n), eval.eval(compile1(parse(s)), env)));
        return env;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /* ----------------------- AST ------------------------ */
    interface Expr {
        default String stringfy() {
            return CodeGen.scheme(this);
        }
    }
    class Sym implements Expr {
        final String name;
        private Sym(String name) {
            this.name = name;
        }
        @Override public String toString() {
            return stringfy();
        }
        static final Map<String, Sym> cache = new HashMap<>();
        static Sym of(String name) {
            return cache.computeIfAbsent(name, t -> new Sym(name));
        }
    }
    class App implements Expr {
        final Expr abs;
        final Expr arg;
        App(Expr abs, Expr arg) {
            this.abs = abs;
            this.arg = arg;
        }
        @Override public String toString() {
            return stringfy();
        }
    }
    class Abs implements Expr {
        final Sym param;
        final Expr body;
        Abs(Sym param, Expr body) {
            this.param = param;
            this.body = body;
        }
        @Override public String toString() {
            return stringfy();
        }
    }


    interface Visitor<V, C> {
        V visit(Sym s, C ctx);
        V visit(App s, C ctx);
        V visit(Abs s, C ctx);

        default V visit(Expr s, C ctx) {
            if (s instanceof Sym) {
                return visit(((Sym) s), ctx);
            } else if (s instanceof App) {
                return visit(((App) s), ctx);
            } else if (s instanceof Abs) {
                return visit(((Abs) s), ctx);
            } else {
                throw new UnsupportedOperationException();
            }
        }
    }

    /* ----------------------- Value ---------------------- */
    interface Val { }
    class Closure implements Val {
        final Abs abs;
        final Env<Val> env;

        Closure(Abs abs, Env<Val> env) {
            this.abs = abs;
            this.env = env;
        }

        @Override public String toString() {
            return CodeGen.scheme(abs);
        }
    }


    /* ------------------ Interpreter ---------------------- */
    @SuppressWarnings("MapOrSetKeyShouldOverrideHashCodeEquals")
    class Env<V> {
        final Map<Sym, V> env = new LinkedHashMap<>();
        final /*@Nullable*/ Env<V> parent;

        Env(/*@Nullable*/ Env<V> parent) {
            this.parent = parent;
        }

        // define
        void put(Sym var, V val) {
            assert !env.containsKey(var); // redefine
            env.put(var, val);
        }

        V lookup(Sym sym) {
            V val = env.get(sym);
            if (val != null) {
                return val;
            } else if (parent != null) {
                return parent.lookup(sym);
            } else {
                throw new RuntimeException(sym + " not found");
            }
        }
    }

    interface Eval<From, To> {
        To eval(From s, Env<To> env);
    }

    // 这个解释器的环境其实没什么卵用, 编译器已经把自由变量干掉了, 也不需要环境, 仅仅在做代换处理
    class Interp implements Visitor<Val, Env<Val>>, Eval<Expr, Val> {
        final static Eval<Expr, Val> self = new Interp();

        @Override public Val visit(Sym s, Env<Val> env) {
            return env.lookup(s);
        }
        @Override public Val visit(App s, Env<Val> env) {
            return apply(s, env);
        }
        @Override public Val visit(Abs s, Env<Val> env) {
            return new Closure(s, env);
        }

        // eval - apply

        @Override
        public Val eval(Expr s, Env<Val> env) {
            return visit(s, env);
        }

        Val apply(App app, Env<Val> env) {
            Val val = eval(app.abs, env);
            if (val instanceof Closure) {
                Closure closure = ((Closure) val);
                Env<Val> subEnv = new Env<>(closure.env);
                Val arg = eval(app.arg, env);
                subEnv.put(closure.abs.param, arg);
                return eval(closure.abs.body, subEnv);
            } else {
                return val;
            }
        }
    }


    /* ---------------------- Parser ------------------------ */
    class Parser {
        static ScriptEngine JS = new ScriptEngineManager().getEngineByName("javascript");

        // String ->  Map | List | String | Integer
        public static Object parse(String s) {
            try {
                return JS.eval("Java.asJSONCompatible(" + s + ")");
            } catch (ScriptException e) {
                throw new RuntimeException(e);
            }
        }
    }



    /* --------------------  Compiler ----------------------- */
    @SuppressWarnings("rawtypes")
    class Compiler implements Eval<Object, Expr> {
        final static Compiler self = new Compiler();

        static Object Y = parse(S_Y);
        static Object NIL = parse(S_NIL);

        @Override
        public Expr eval(Object s, Env<Expr> env) {
            return compile(s, env);
        }

        static class Expander implements Visitor<Expr, Env<Expr>>, Eval<Expr, Expr> {
            final static Eval<Expr, Expr> self = new Expander();

            @Override public Expr visit(Sym s, Env<Expr> env) {
                return env.lookup(s);
            }
            @Override public Expr visit(App s, Env<Expr> env) {
                return new App(visit(s.abs, env), visit(s.arg, env));
            }
            @Override public Expr visit(Abs s, Env<Expr> env) {
                // close term, 干掉 free var
                Env<Expr> subEnv = new Env<>(env);
                subEnv.put(s.param, s.param);
                Expr body = visit(s.body, subEnv);
                return new Abs(s.param, body);
            }

            @Override public Expr eval(Expr s, Env<Expr> env) {
                return visit(s, env);
            }
        }

        static Expr compile(Object s, Env<Expr> env) {
            return expander().eval(compile1(s), env);
        }

        // 注意: 不能直接替换代码, 一个case (λ (+) (+ 0 0))
        static Expr compile1(Object exp) {
            if (exp instanceof List) {
                List s = ((List) exp);
                int sz = s.size();
                // if (sz == 0) return Sym.of(NIL);
                assert !s.isEmpty();

                Object car = s.get(0);

                // Lambdas
                if (LAMBDA.equals(car)) {
                    return compileLambda(s);
                }

                // Conditionals
                else if (IF.equals(car)) {
                    // [if, cond, then, orElse] => [cond, [lambda, [], then], [lambda, [], orElse]]
                    assert sz == 4;
                    Object cond = s.get(1);
                    List then = list(LAMBDA, list(), s.get(2));
                    List orElse = list(LAMBDA, list(), s.get(3));
                    return compile1(list(cond, then, orElse));
                } else if (AND.equals(car)) {
                    // [and, a, b] => [if, a, b, #f]
                    assert sz == 3;
                    return compile1(list(IF, s.get(1), s.get(2), FALSE));
                } else if (OR.equals(car)) {
                    // [or, a, b] => [if, a, #t, b]
                    assert sz == 3;
                    return compile1(list(IF, s.get(1), TRUE, s.get(2)));
                }

                // Binding Forms
                else if (LET.equals(car)) {
                    return compileLet(s);
                } else if (LET_REC.equals(car)) {
                    return compileLetRec(s);
                }

                // Quote
                else if (QUOTE.equals(car)) {
                    assert sz == 2;
                    Object cdr = s.get(1);
                    // 只支持 ['quote', []] 表达 nil
                    assert cdr instanceof List;
                    assert ((List<?>) cdr).isEmpty();
                    return compile1(NIL);
                }

                // Application -- must be last
                else {
                    return compileApply(s);
                }
            }

            // Numerals
            else if (exp instanceof Integer) {
                // church-numeral
                return compile1(churchNumeral(((Integer) exp)));
            }

            // symbol
            else if (exp instanceof String) {
                return Sym.of(((String) exp));
            }

            else {
                throw new IllegalStateException();
            }
        }

        static Expr compileLet(List lst) {
            // [let, [[v1, exp1], ... [vN, expN]], body] => [[lambda, [v1, ... vN], body], exp1, ... expN]
            int sz = lst.size();
            assert sz == 3;
            assert lst.get(1) instanceof List;
            List pairs = (List) lst.get(1);
            List<String> params = new ArrayList<>(pairs.size());
            List<Object> args = new ArrayList<>(pairs.size());
            for (Object pair : pairs) {
                assert pair instanceof List && ((List) pair).size() == 2;
                Object param = ((List) pair).get(0);
                assert param instanceof String;
                params.add(((String) param));
                args.add(((List) pair).get(1));
            }
            List<Object> let = list(list(LAMBDA, params, lst.get(2)));
            let.addAll(args);
            return compile1(let);
        }

        static Expr compileLetRec(List lst) {
            // ['letrec', [['f', 'lam']], 'body'] => ['let', [['f', ['Y', ['λ', ['f'], 'lam']]]], 'body']
            int sz = lst.size();
            assert sz == 3;
            assert lst.get(1) instanceof List;
            List pairs = (List) lst.get(1);
            assert pairs.size() == 1;
            Object pair0 = pairs.get(0);
            assert pair0 instanceof List && ((List) pair0).size() == 2;
            List pair = (List) pair0;
            Object f = pair.get(0);
            Object lam = pair.get(1);
            Object body = lst.get(2);
            List let = list(LET, list(list(f, list(Y, list(LAMBDA, list(f), lam)))), body);
            return compile1(let);
        }

        static Expr compileLambda(List lst) {
            // Currying
            // [lambda, [v1, ... vN], body] => [lambda, [v1], [lambda, [v2], ... [lambda, [vN], body]]]
            int sz = lst.size();
            assert sz == 3;
            Object params = lst.get(1);
            Object body = lst.get(2);

            assert params instanceof List;
            List paramLst = (List) params;
            int paramSz = paramLst.size();
            if (paramSz == 0) {
                return new Abs(Sym.of("_"), compile1(body));
            } else if (paramSz == 1) {
                Expr param = compile1(paramLst.get(0));
                assert param instanceof Sym;
                return new Abs((Sym) param, compile1(body));
            } else {
                Expr param = compile1(paramLst.get(0));
                assert param instanceof Sym;
                List subLam = list(LAMBDA, paramLst.subList(1, paramSz), body);
                return new Abs((Sym) param, compile1(subLam));
            }
        }

        static Expr compileApply(List lst) {
            int sz = lst.size();
            // Currying
            // [f, arg1, ... argN] => [... [[f, arg1], arg2], ... argN]
            // [a, b, c, c] => [[[a, b], c], d]
            if (sz == 1) {
                return new App(compile1(lst.get(0)), Sym.of(VOID));
            } else if (sz == 2) {
                return new App(compile1(lst.get(0)), compile1(lst.get(1)));
            } else {
                return new App(compile1(lst.subList(0, lst.size() - 1)), compile1(lst.get(lst.size() - 1)));
            }
        }

        static Object churchNumeralApplyN(String z, String f, int n) {
            assert n >= 0;
            if (n == 0) {
                return z;
            } else {
                return list(f, churchNumeralApplyN(z, f, n - 1));
            }
        }

        // 丘齐数就是将 f 应用到 z 的次数
        static Object churchNumeral(int nat) {
            assert nat >= 0;
            String f = "f";
            String z = "z";
            if (nat == 0) {
                // [lambda, [f], [lambda, [z], z]]
                return list(LAMBDA, list(f), list(LAMBDA, list(z), z));
            } else {
                // [lambda, [f], [lambda, [z] [$apply-n $n]]]
                return list(LAMBDA, list(f), list(LAMBDA, list(z), churchNumeralApplyN(z, f, nat)));
            }
        }

        static List<Object> list(Object ...its) {
            List<Object> lst = new ArrayList<>(its.length);
            Collections.addAll(lst, its);
            return lst;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


    interface FFI {

        interface F extends Function<F, F> { }

        class Compiler implements Visitor<F, Env<F>>, Eval<Expr, F> {
            final static Eval<Expr, F> self = new Compiler();
            @Override public F visit(Sym s, Env<F> env) {
                return env.lookup(s);
            }
            @Override public F visit(App s, Env<F> env) {
                return visit(s.abs, env).apply(visit(s.arg, env));
            }
            @Override public F visit(Abs s, Env<F> env) {
                return arg -> {
                    Env<F> subEnv = new Env<>(env);
                    subEnv.put(s.param, arg);
                    return visit(s.body, subEnv);
                };
            }
            @Override public F eval(Expr s, Env<F> env) {
                return visit(s, env);
            }
        }

        class Pair<T> implements Val {
            final T car;
            final Pair<T> cdr;

            Pair(T car, Pair<T> cdr) {
                this.car = car;
                this.cdr = cdr;
            }

            @SafeVarargs
            static <T> Pair<T> of(T ...els) {
                int sz = els.length;
                if (sz == 0) {
                    return new Pair<>(null, null);
                } else {
                    Pair<T> pair = new Pair<>(null, null);
                    for (int i = sz - 1; i >= 0; i--) {
                        pair = new Pair<>(els[i], pair);
                    }
                    return pair;
                }
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                return Objects.equals(car, ((Pair<?>) o).car) && Objects.equals(cdr, ((Pair<?>) o).cdr);
            }

            @Override
            public int hashCode() {
                return Objects.hash(car, cdr);
            }

            @Override public String toString() {
                if (car == null && cdr == null) {
                    return "nil";
                } else {
                    return "(" + car + " " + cdr + ")";
                }
            }
        }

        class VF<T> implements F {
            final T val;
            VF(T val) {
                this.val = val;
            }
            @Override public F apply(F f) {
                return nil.apply(f);
            }
        }

        F nil = f -> f;
        F succ = n -> f -> z -> f.apply(n.apply(f.apply(z))); // (λ (n) (λ (f) (λ (z) (f (n (f z))))))
        F zero = f -> null;
        F True = f -> f;
        F False = f -> f;

        Eval<F, Integer>        natifier    = (f, env) -> natify(f);
        Eval<F, Boolean>        boolifier   = (f, env) -> boolify(f);
        Eval<F, Pair<Integer>>  natListify  = (f, env) -> natListify(f);
        Eval<F, Pair<Boolean>>  boolListify = (f, env) -> boolListify(f);

        interface UnChurchification<T> {
            T unChurchify(F churchEncoded);
        }

        static int natify(F churchNumeral) {
            F f = churchNumeral.apply(succ).apply(zero);
            int i = -1;
            while (f != null) {
                f = f.apply(nil);
                i++;
            }
            return i;
        }

        static boolean boolify(F churchBoolean) {
            F f = churchBoolean.apply(a -> True).apply(a -> False);
            if (f == True) {
                return true;
            } else if (f == False) {
                return false;
            } else {
                throw new IllegalStateException();
            }
        }

        static <T> Pair<T> listify(UnChurchification<T> unChurch, F churchList) {
            F onCons = car -> cdr -> new VF<>(new Pair<>(unChurch.unChurchify(car), listify(unChurch, cdr)));
            F onNil = nil -> new VF<>(new Pair<>(null, null));
            //noinspection unchecked
            VF<Pair<T>> vf = ((VF<Pair<T>>) churchList.apply(onCons).apply(onNil));
            return vf.val;
        }

        static Pair<Integer> natListify(F churchList) {
            return listify(FFI::natify, churchList);
        }

        static Pair<Boolean> boolListify(F churchList) {
            return listify(FFI::boolify, churchList);
        }
    }

    interface Names {
        String VOID = "nothing";
        String LAMBDA = "λ";
        String QUOTE = "quote";

        // Boolean and conditionals
        String TRUE = "#t";
        String FALSE = "#f";
        String IF = "if";
        String AND = "and";
        String OR = "or";

        // Numerals
        String IS_ZERO = "zero?";
        String SUM = "+";
        String SUB = "-";
        String MUL = "*";
        String EQ = "=";

        // Lists
        // String NIL = "nil";
        String CONS = "cons";
        String CAR = "car";
        String CDR = "cdr";
        String IS_PAIR = "pair?";
        String IS_NULL = "null?";

        // Bindings
        String LET = "let";
        String LET_REC = "letrec";
    }

    interface Primitives {
        // 这里不用定义成单参的函数, compile1 会做 curry 处理

        String S_Y = "[['λ', ['y'], ['λ', ['F'], ['F', ['λ', ['x'], [[['y', 'y'], 'F'], 'x']]]]],\n" +
                     " ['λ', ['y'], ['λ', ['F'], ['F', ['λ', ['x'], [[['y', 'y'], 'F'], 'x']]]]]]";

        // (λ (void) void)
        String S_VOID = "['λ', ['" + VOID + "'], '" + VOID + "']";

        // (λ (on_cons) (λ (on_nil) (on_nil ,S_VOID)) )
        // (λ (on_cons on_nil) (on_nil ,S_VOID))
        String S_NIL = "['λ', ['on_cons', 'on_nil'], ['on_nil', " + S_VOID + "]]";

        // (λ (t) (λ (f) (t ,S_VOID)))
        String S_TRUE = "['λ', ['t', 'f'], ['t', '" + VOID + "']]";
        // (λ (t) (λ (f) (f ,S_VOID)))
        String S_FALSE = "['λ',['t', 'f'], ['f', '" + VOID + "']]";
        // (λ (n) ((n (λ (_) ,S_FALSE)) ,S_TRUE))
        String S_IS_ZERO = "['λ', ['n'], [['n', ['λ', ['_'], '" + FALSE + "']], '" + TRUE + "']]";


        // (λ (n) (λ (f) (λ (z) (f (n (f z))))))
        // (λ (n f z) (f (n f z)))
        String S_SUCC = "['λ', ['n', 'f', 'z'], ['f', ['n', 'f', 'z']]]";

        // (λ (n) (λ (m) (λ (f) (λ (z) ((m f) ((n f) z))))))
        // (λ (n m)  (λ (f z) (m f (n f z))))
        // (λ (n m f z)  (m f (n f z)))
        String S_SUM = "['λ', ['n', 'm', 'f', 'z'], ['m', 'f', ['n', 'f', 'z']]]";

        // (λ (n) (λ (m) (λ (f) (λ (z) ((m (n f)) z)))))
        // (λ (n m)  (λ (f z) (m (n f) z)))
        // (λ (n m f z) (m (n f) z))
        String S_MUL = "['λ', ['n', 'm', 'f', 'z'], ['m', ['n', 'f'], 'z']]";

        // (λ (n) (λ (f) (λ (z) (((n (λ (g) (λ (h) (h (g f))))) (λ (u) z)) (λ (u) u)))))
        // (λ (n f z) (n   (λ (g h) (h (g f)))    (λ (u) z)    (λ (u) u)  ))
        String S_PRED = "['λ', ['n', 'f', 'z'],  ['n',   ['λ', ['g', 'h'], ['h', ['g', 'f']]],   ['λ', ['u'], 'z'],    ['λ', ['u'], 'u']  ]]";

        // (λ (n) (λ (m) ((m ,S_PRED) n)))
        // (λ (n m) (m ,S_PRED n))
        String S_SUB = "['λ', ['n', 'm'], ['m', " + S_PRED + ", 'n']]";

        // (λ (x y) (and (zero? (- x y)) (zero? (- y x))))
        String S_EQ = "['λ', ['x', 'y'], ['" + AND + "',   ['"+IS_ZERO+"', ['-', 'x', 'y']],   ['"+IS_ZERO+"', ['-', 'y', 'x']]]]";

        // (λ (car) (λ (cdr) (λ (on_cons) (λ (on_nil) ((on_cons car) cdr)))))
        // (λ (car cdr on_cons on_nil) (on_cons car cdr))
        String S_CONS = "['λ', ['car', 'cdr', 'on_cons', 'on_nil'], ['on_cons', 'car', 'cdr']]";

        // (λ (_) ( (λ (f) (f f) (λ (f) (f f)) )))
        String S_ERROR = "['λ', ['_'], [['λ', ['f'], ['f', 'f']], ['λ', ['f'], ['f', 'f']]]]";

        // (λ (list) ((list (λ (car) (λ (cdr) car))) ,S_ERROR))
        // (λ (list) (list (λ (car cdr) car) ,S_ERROR))
        String S_CAR = "['λ', ['list'], ['list', ['λ', ['car', 'cdr'], 'car'], " + S_ERROR + "]]";

        // (λ (list) ((list (λ (car) (λ (cdr) cdr))) ,ERROR))
        // (λ (list) (list (λ (car cdr) cdr) ,S_ERROR))
        String S_CDR = "['λ', ['list'], ['list', ['λ', ['car', 'cdr'], 'cdr'], " + S_ERROR + "]]";

        // (λ (list) ((list (λ (_) (λ (_) ,S_TRUE))) (λ (_) ,S_FALSE)))
        // (λ (list) (list (λ (_1 _2) ,S_TRUE) (λ (_) ,S_FALSE)))
        String S_IS_PAIR = "['λ', ['list'], ['list', ['λ', ['_1', '_2'], '" + TRUE + "'], ['λ', ['_'], '" + FALSE + "']]]";

        // (λ (list) ((list (λ (_) (λ (_) ,S_FALSE))) (λ (_) ,S_TRUE)))
        // (λ (list) (list (λ (_1 _2) ,S_FALSE) (λ (_) ,S_TRUE)))
        String S_IS_NULL = "['λ', ['list'], ['list', ['λ', ['_1', '_2'], '" + FALSE + "'], ['λ', ['_'], '" + TRUE + "']]]";
    }
}
