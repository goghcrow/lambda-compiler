package xiao;

import static xiao.λ.*;
import static xiao.λ.Names.*;

/**
 * CodeGen & PrettyPrinter
 * @author chuxiaofeng
 */
public interface CodeGen extends Visitor<String, Void> {

    static String scheme(Expr s) { return scheme.visit(s, null); }
    static String py(Expr s) { return py.visit(s, null); }
    static String js(Expr s) { return js.visit(s, null); }
    static String json(Expr s) { return json.visit(s, null); }


    // natify : (+ n 1)(0)
    // boolity: (λ () #t)(λ () #f)
    CodeGen scheme = new CodeGen() {
        @Override public String visit(Sym s, Void v) {
            return s.name;
        }
        @Override public String visit(App s, Void v) {
            return "(" + visit(s.abs, v) + " " + visit(s.arg, v) + ")";
        }
        @Override public String visit(Abs s, Void v) {
            return "(" + LAMBDA + " (" + visit(s.param, v) + ") " + visit(s.body, v) + ")";
        }
    };

    CodeGen json = new CodeGen() {
        @Override public String visit(Sym s, Void v) {
            return "'" + s.name + "'";
        }
        @Override public String visit(App s, Void v) {
            return "[" + visit(s.abs, v) + ", " + visit(s.arg, v) + "]";
        }
        @Override public String visit(Abs s, Void v) {
            return "['" + LAMBDA + "', [" + visit(s.param, v) + "], " + visit(s.body, v) + "]";
        }
    };

    // natify : (n => n + 1)(0)
    // boolify: (_ => true)(_ => false)
    // (() => { let unchurchify = (churched) => churched(car => cdr => [car(n => n+1)(0), unchurchify(cdr)])(nil => null); return unchurchify })()(%s)
    CodeGen js = new CodeGen() {
        @Override public String visit(Sym s, Void v) {
            return s.name;
        }
        @Override public String visit(App s, Void v) {
            if (s.abs instanceof Sym) {
                return visit(s.abs, v) + "(" + visit(s.arg, v) + ")";
            } else {
                return "(" + visit(s.abs, v) + ")" + "(" + visit(s.arg, v) + ")";
            }
        }
        @Override public String visit(Abs s, Void v) {
            return "(" + visit(s.param, v) + " => " + visit(s.body, v) + ")";
        }
    };

    // natify : (lambda n: n + 1)(0)
    // boolify: (lambda _: true)(lambda _: false)
    CodeGen py = new CodeGen() {
        @Override public String visit(Sym s, Void v) {
            return s.name;
        }
        @Override public String visit(App s, Void v) {
            return "((" + visit(s.abs, v) + ")(" + visit(s.arg, v) + "))";
        }
        @Override public String visit(Abs s, Void v) {
            return "(lambda " + s.param.name + ": (" + visit(s.body, v) + "))";
        }
    };
}
