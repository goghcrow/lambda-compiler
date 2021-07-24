# λ

> Lambda Calculus Compiler Java Implemented

- 代码分为几块:
     - Parser : 把代码 (语法用 json array 来表达 s-expr) 转换成 java list 的 s-expr
     - Compiler + AST : Compiler 这里做了 desugar 的工作, 把表层语言(scheme 子集, 语法参见注释) 编译成 core language (pure lambda) 并消除 free variable
     - Interpreter + Value : 把 AST 解释成值
     - FFI : 把 Value 转换成宿主语言的值, 这里是把 Closure 转换成 java value
     - PrettyPrinter : 也可以当成 CodeGen 用，雾

- 大致流程： json-s-expr -> pure-lambda-s-expr -> closure


## type

```
 Sym      = String
 Env      = Sym -> Val
 Val      = Closure
 Closure  = Abs * Env
 S-Expr   = List | Map | String | Integer
 Parser   = String -> S-Expr
 Expr     = Sym | Abs | App
 Compiler = S-Expr -> Expr
 Apply    = Val * Val -> Val
 Eval     = Expr * Env -> Val
```

## 名词

```
 Sym : Variable Reference, Term  (term > sym > var)
 Abs : Abstraction, Lambda, Anonymous Functions
 App : Application, Call
 bound var :
 unbound var : free var
 open term : term with unbound var
     a closure closes an open term
     λa.ab
      a of ab: bound var
      b of ab: unbound var
```

## scheme 子集语法：
```
  <exp> ::= <var>
         |  #t
         |  #f
         |  (if  <exp> <exp> <exp>)
         |  (and <exp> <exp>)
         |  (or  <exp> <exp>)
         |  <nat>
         |  (zero? <exp>)
         |  (- <exp> <exp>)
         |  (= <exp> <exp>)
         |  (+ <exp> <exp>)
         |  (* <exp> <exp>)
         |  <lam>
         |  (let ((<var> <exp>) ...) <exp>)
         |  (letrec ((<var> <lam>)) <exp>)
         |  (cons <exp> <exp>)
         |  (car  <exp>)
         |  (cdr  <exp>)
         |  (pair? <exp>)
         |  (null? <exp>)
         |  '()
         |  (<exp> <exp> ...)
  <lam> ::= (λ (<var> ...) <exp>)
```

> 参考  https://matt.might.net/articles/compiling-up-to-lambda-calculus/
原来直接模式匹配处理代换的逻辑有问题, 一个case (λ (+) (+ 0 0))