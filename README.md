# Source to Source λ Calculus Compiler

- 代码分为几块:
     - Parser : 把代码 (语法用 json array 来表达 s-expr) 转换成 java list 的 s-expr
     - Compiler : desugar, 把表层语言(scheme 子集, 语法参见注释) 编译成 core language (pure lambda) 并消除 free variable, 返回 AST
     - ~Interpreter + Value : 把 AST 解释成 Value (即Closure)~(废弃)
     - ~UnChurchification : 把 Value 转换成宿主语言的值, 这里是把 Closure 转换成 java value~ (废弃)
     - UnChurchification : 把 pure lambda 编译成 java lambda, 计算对应的 java value
     - CodeGen : pure lambda 生成其他语言代码

- 大致流程： json-s-expr -> pure-lambda-s-expr -> closure


## type

```
 Sym      = Str
 Abs      = Sym * Expr
 App      = Expr * Expr
 Expr     = Sym | Abs | App

 Val      = Closure
 Env      = Sym -> Val
 Closure  = Abs * Env

 Apply    = Val * Val -> Val
 Eval     = Expr * Env -> Val

 S-Expr   = List | Map | String | Integer
 Parser   = Str -> S-Expr
 Compiler = S-Expr -> Expr

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

## 源语言：scheme 子集语法：

```
  <exp> ::= <var>
         |  #t
         |  #f
         |  (if  <exp> <exp> <exp>)
         |  (and <exp> <exp>)
         |  (or  <exp> <exp>)
         |  (not  <exp> <exp>)
         |  <nat>
         |  <str>
         |  <sym>
         |  (zero? <exp>)
         |  (= <exp> <exp>)
         |  (!= <exp> <exp>)
         |  (< <exp> <exp>)
         |  (> <exp> <exp>)
         |  (<= <exp> <exp>)
         |  (>= <exp> <exp>)
         |  (+ <exp> <exp>)
         |  (- <exp> <exp>)
         |  (* <exp> <exp>)
         |  (/ <exp> <exp>)
         |  (^ <exp> <exp>)
         |  (% <exp> <exp>)
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

## 目标语言：pure lambda 语法

```
  <exp> ::= <var> | <lam> | (<exp> <exp>)
  <lam> ::= (λ (<var>) <exp>)
```

> 参考  https://matt.might.net/articles/compiling-up-to-lambda-calculus/
原来直接模式匹配处理代换的逻辑有问题, 一个case (λ (+) (+ 0 0))

## DEMO

Javascript Unchurchification

```js
const unchurchifyBool = churched => churched(_ => true)(_ => false);
const unchurchifyNat = churched => churched(n => n + 1)(0);
const unchurchifyString = churched => churched(car => cdr => String.fromCharCode(unchurchifyNat(car)) + unchurchifyString(cdr))(nil => '');
const unchurchifyList = (unchurchification, churched) => churched(car => cdr => [unchurchification(car), unchurchifyList(unchurchification, cdr)])(nil => null);
```

### Hello World!

```java
String hello = "'\"Hello World!\"'";
System.out.println(λ.compile(hello, CodeGen.js));
System.out.println(λ.compile(hello, CodeGen.java).string()); // Hello World!
```

js outout
> ```(((car => (cdr => (on_cons => (on_nil => (on_cons(car))(cdr))))))((f => (z => f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(z))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))((((car => (cdr => (on_cons => (on_nil => (on_cons(car))(cdr))))))((f => (z => f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(z)))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))((((car => (cdr => (on_cons => (on_nil => (on_cons(car))(cdr))))))((f => (z => f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(z))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))((((car => (cdr => (on_cons => (on_nil => (on_cons(car))(cdr))))))((f => (z => f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(z))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))((((car => (cdr => (on_cons => (on_nil => (on_cons(car))(cdr))))))((f => (z => f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(z)))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))((((car => (cdr => (on_cons => (on_nil => (on_cons(car))(cdr))))))((f => (z => f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(z))))))))))))))))))))))))))))))))))))((((car => (cdr => (on_cons => (on_nil => (on_cons(car))(cdr))))))((f => (z => f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(z)))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))((((car => (cdr => (on_cons => (on_nil => (on_cons(car))(cdr))))))((f => (z => f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(z)))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))((((car => (cdr => (on_cons => (on_nil => (on_cons(car))(cdr))))))((f => (z => f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(z))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))((((car => (cdr => (on_cons => (on_nil => (on_cons(car))(cdr))))))((f => (z => f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(z))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))((((car => (cdr => (on_cons => (on_nil => (on_cons(car))(cdr))))))((f => (z => f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(z))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))((((car => (cdr => (on_cons => (on_nil => (on_cons(car))(cdr))))))((f => (z => f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(z)))))))))))))))))))))))))))))))))))))((on_cons => (on_nil => on_nil((nothing => nothing))))))))))))))))```

```js
console.log(unchurchifyString( ... )) // Hello World!
```

### Fact

```java
String fact5 =
    "['letrec', " +
        "[['fact', ['λ', ['n'], ['if', ['=', 'n', 0], 1, ['*', 'n', ['fact', ['-', 'n', 1]]]]]]]," +
        " ['fact', 5]]";
System.out.println(λ.compile(fact5, CodeGen.js));
System.out.println(λ.compile(fact5, CodeGen.java).nat()); // 120
```

js output
> ```((fact => fact((f => (z => f(f(f(f(f(z))))))))))((((y => (F => F((x => ((y(y))(F))(x))))))((y => (F => F((x => ((y(y))(F))(x)))))))((fact => (n => (((((x => (y => ((((n => (n((_ => (t => (f => f((nothing => nothing)))))))((t => (f => t((nothing => nothing)))))))((((n => (m => (m((n => (f => (z => ((n((g => (h => h(g(f))))))((u => z)))((u => u)))))))(n))))(x))(y)))((_ => ((n => (n((_ => (t => (f => f((nothing => nothing)))))))((t => (f => t((nothing => nothing)))))))((((n => (m => (m((n => (f => (z => ((n((g => (h => h(g(f))))))((u => z)))((u => u)))))))(n))))(y))(x)))))((_ => (t => (f => f((nothing => nothing)))))))))(n))((f => (z => z))))((_ => (f => (z => f(z))))))((_ => (((n => (m => (f => (z => (m(n(f)))(z))))))(n))(fact((((n => (m => (m((n => (f => (z => ((n((g => (h => h(g(f))))))((u => z)))((u => u)))))))(n))))(n))((f => (z => f(z))))))))))))```

```js
console.log(unchurchifyNat( ... )) // 120
```


### FizzBuzz

code
```java
/*
['letrec', [['fizzbuzz', ['λ', ['i', 's'],
   ['if', ['<=', 'i', 100],
       ['if', ['=', ['%', 'i', 15], 0],
           ['fizzbuzz', ['+', 'i', 1], ['cons', '"FizzBuzz"', 's']],
           ['if', ['=', ['%', 'i', 3], 0],
               ['fizzbuzz', ['+', 'i', 1], ['cons', '"Fizz"', 's']],
               ['if', ['=', ['%', 'i', 5], 0],
                   ['fizzbuzz', ['+', 'i', 1], ['cons', '"Buzz"', 's']],
                   ['fizzbuzz', ['+', 'i', 1], ['cons', ['if', ['<', 'i', 10],
                                                               ['cons', ['+', 48, 'i'], ['quote', []]],
                                                               ['cons', ['+', 48, ['/', 'i', 10]], ['cons', ['+', 48, ['%', 'i', 10]], ['quote', []]]]],                                                       's']]]]],
       's']]]],
      ['fizzbuzz', 1, ['quote', []]]]
*/
String fizzbuzz = "['letrec', [['fizzbuzz', ['λ', ['i', 's'],\n" +
        "   ['if', ['<=', 'i', 100],\n" +
        "       ['if', ['=', ['%', 'i', 15], 0],\n" +
        "           ['fizzbuzz', ['+', 'i', 1], ['cons', '\"FizzBuzz\"', 's']],\n" +
        "           ['if', ['=', ['%', 'i', 3], 0],\n" +
        "               ['fizzbuzz', ['+', 'i', 1], ['cons', '\"Fizz\"', 's']],\n" +
        "               ['if', ['=', ['%', 'i', 5], 0],\n" +
        "                   ['fizzbuzz', ['+', 'i', 1], ['cons', '\"Buzz\"', 's']],\n" +
        "                   ['fizzbuzz', ['+', 'i', 1], ['cons', \n" +
        "                                                       ['if', ['<', 'i', 10], \n" +
        "                                                               ['cons', ['+', 48, 'i'], ['quote', []]],\n" +
        "                                                               ['cons', ['+', 48, ['/', 'i', 10]], ['cons', ['+', 48, ['%', 'i', 10]], ['quote', []]]]],\n" +
        "                                                       's']]]]],\n" +
        "       's']]]],\n" +
        "      ['fizzbuzz', 1, ['quote', []]]]\n";
System.out.println(λ.compile(fizzbuzz, CodeGen.js));

Pair<String> s = λ.compile(fizzbuzz, CodeGen.java).list(UnChurchification::stringify);
List<String> lst = s.list();
Collections.reverse(lst);
System.out.println(lst);
```

> ```[1, 2, Fizz, 4, Buzz, Fizz, 7, 8, Fizz, Buzz, 11, Fizz, 13, 14, FizzBuzz, 16, 17, Fizz, 19, Buzz, Fizz, 22, 23, Fizz, Buzz, 26, Fizz, 28, 29, FizzBuzz, 31, 32, Fizz, 34, Buzz, Fizz, 37, 38, Fizz, Buzz, 41, Fizz, 43, 44, FizzBuzz, 46, 47, Fizz, 49, Buzz, Fizz, 52, 53, Fizz, Buzz, 56, Fizz, 58, 59, FizzBuzz, 61, 62, Fizz, 64, Buzz, Fizz, 67, 68, Fizz, Buzz, 71, Fizz, 73, 74, FizzBuzz, 76, 77, Fizz, 79, Buzz, Fizz, 82, 83, Fizz, Buzz, 86, Fizz, 88, 89, FizzBuzz, 91, 92, Fizz, 94, Buzz, Fizz, 97, 98, Fizz, Buzz]```

js output
> ```((fizzbuzz => (fizzbuzz((f => (z => f(z)))))((on_cons => (on_nil => on_nil((nothing => nothing)))))))((((y => (F => F((x => ((y(y))(F))(x))))))((y => (F => F((x => ((y(y))(F))(x)))))))((fizzbuzz => (i => (s => (((((m => (n => ((n => (n((_ => (t => (f => f((nothing => nothing)))))))((t => (f => t((nothing => nothing)))))))((((n => (m => (m((n => (f => (z => ((n((g => (h => h(g(f))))))((u => z)))((u => u)))))))(n))))(m))(n)))))(i))((f => (z => f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(z))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))((_ => (((((x => (y => ((((n => (n((_ => (t => (f => f((nothing => nothing)))))))((t => (f => t((nothing => nothing)))))))((((n => (m => (m((n => (f => (z => ((n((g => (h => h(g(f))))))((u => z)))((u => u)))))))(n))))(x))(y)))((_ => ((n => (n((_ => (t => (f => f((nothing => nothing)))))))((t => (f => t((nothing => nothing)))))))((((n => (m => (m((n => (f => (z => ((n((g => (h => h(g(f))))))((u => z)))((u => u)))))))(n))))(y))(x)))))((_ => (t => (f => f((nothing => nothing)))))))))((((((y => (F => F((x => ((y(y))(F))(x))))))((y => (F => F((x => ((y(y))(F))(x)))))))((mod => (m => (n => (((((x => (y => ((((n => (n((_ => (t => (f => f((nothing => nothing)))))))((t => (f => t((nothing => nothing)))))))((((n => (m => (m((n => (f => (z => ((n((g => (h => h(g(f))))))((u => z)))((u => u)))))))(n))))(x))(y)))((_ => ((n => (n((_ => (t => (f => f((nothing => nothing)))))))((t => (f => t((nothing => nothing)))))))((((n => (m => (m((n => (f => (z => ((n((g => (h => h(g(f))))))((u => z)))((u => u)))))))(n))))(y))(x)))))((_ => (t => (f => f((nothing => nothing)))))))))(n))((f => (z => z))))((_ => (_ => ((f => f(f)))((f => f(f)))))))((_ => (((((m => (n => ((n => (n((_ => (t => (f => f((nothing => nothing)))))))((t => (f => t((nothing => nothing)))))))((((n => (m => (m((n => (f => (z => ((n((g => (h => h(g(f))))))((u => z)))((u => u)))))))(n))))(m))(n)))))(n))(m))((_ => (mod((((n => (m => (m((n => (f => (z => ((n((g => (h => h(g(f))))))((u => z)))((u => u)))))))(n))))(m))(n)))(n))))((_ => m)))))))))(i))((f => (z => f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(z))))))))))))))))))))((f => (z => z))))((_ => (fizzbuzz((((n => (m => (f => (z => (m(f))((n(f))(z)))))))(i))((f => (z => f(z))))))((((car => (cdr => (on_cons => (on_nil => (on_cons(car))(cdr))))))((((car => (cdr => (on_cons => (on_nil => (on_cons(car))(cdr))))))((f => (z => f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(z))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))((((car => (cdr => (on_cons => (on_nil => (on_cons(car))(cdr))))))((f => (z => f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(z)))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))((((car => (cdr => (on_cons => (on_nil => (on_cons(car))(cdr))))))((f => (z => f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(z))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))((((car => (cdr => (on_cons => (on_nil => (on_cons(car))(cdr))))))((f => (z => f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(z))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))((((car => (cdr => (on_cons => (on_nil => (on_cons(car))(cdr))))))((f => (z => f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(z))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))((((car => (cdr => (on_cons => (on_nil => (on_cons(car))(cdr))))))((f => (z => f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(z)))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))((((car => (cdr => (on_cons => (on_nil => (on_cons(car))(cdr))))))((f => (z => f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(z))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))((((car => (cdr => (on_cons => (on_nil => (on_cons(car))(cdr))))))((f => (z => f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(z))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))((on_cons => (on_nil => on_nil((nothing => nothing))))))))))))))(s)))))((_ => (((((x => (y => ((((n => (n((_ => (t => (f => f((nothing => nothing)))))))((t => (f => t((nothing => nothing)))))))((((n => (m => (m((n => (f => (z => ((n((g => (h => h(g(f))))))((u => z)))((u => u)))))))(n))))(x))(y)))((_ => ((n => (n((_ => (t => (f => f((nothing => nothing)))))))((t => (f => t((nothing => nothing)))))))((((n => (m => (m((n => (f => (z => ((n((g => (h => h(g(f))))))((u => z)))((u => u)))))))(n))))(y))(x)))))((_ => (t => (f => f((nothing => nothing)))))))))((((((y => (F => F((x => ((y(y))(F))(x))))))((y => (F => F((x => ((y(y))(F))(x)))))))((mod => (m => (n => (((((x => (y => ((((n => (n((_ => (t => (f => f((nothing => nothing)))))))((t => (f => t((nothing => nothing)))))))((((n => (m => (m((n => (f => (z => ((n((g => (h => h(g(f))))))((u => z)))((u => u)))))))(n))))(x))(y)))((_ => ((n => (n((_ => (t => (f => f((nothing => nothing)))))))((t => (f => t((nothing => nothing)))))))((((n => (m => (m((n => (f => (z => ((n((g => (h => h(g(f))))))((u => z)))((u => u)))))))(n))))(y))(x)))))((_ => (t => (f => f((nothing => nothing)))))))))(n))((f => (z => z))))((_ => (_ => ((f => f(f)))((f => f(f)))))))((_ => (((((m => (n => ((n => (n((_ => (t => (f => f((nothing => nothing)))))))((t => (f => t((nothing => nothing)))))))((((n => (m => (m((n => (f => (z => ((n((g => (h => h(g(f))))))((u => z)))((u => u)))))))(n))))(m))(n)))))(n))(m))((_ => (mod((((n => (m => (m((n => (f => (z => ((n((g => (h => h(g(f))))))((u => z)))((u => u)))))))(n))))(m))(n)))(n))))((_ => m)))))))))(i))((f => (z => f(f(f(z))))))))((f => (z => z))))((_ => (fizzbuzz((((n => (m => (f => (z => (m(f))((n(f))(z)))))))(i))((f => (z => f(z))))))((((car => (cdr => (on_cons => (on_nil => (on_cons(car))(cdr))))))((((car => (cdr => (on_cons => (on_nil => (on_cons(car))(cdr))))))((f => (z => f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(z))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))((((car => (cdr => (on_cons => (on_nil => (on_cons(car))(cdr))))))((f => (z => f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(z)))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))((((car => (cdr => (on_cons => (on_nil => (on_cons(car))(cdr))))))((f => (z => f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(z))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))((((car => (cdr => (on_cons => (on_nil => (on_cons(car))(cdr))))))((f => (z => f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(z))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))((on_cons => (on_nil => on_nil((nothing => nothing))))))))))(s)))))((_ => (((((x => (y => ((((n => (n((_ => (t => (f => f((nothing => nothing)))))))((t => (f => t((nothing => nothing)))))))((((n => (m => (m((n => (f => (z => ((n((g => (h => h(g(f))))))((u => z)))((u => u)))))))(n))))(x))(y)))((_ => ((n => (n((_ => (t => (f => f((nothing => nothing)))))))((t => (f => t((nothing => nothing)))))))((((n => (m => (m((n => (f => (z => ((n((g => (h => h(g(f))))))((u => z)))((u => u)))))))(n))))(y))(x)))))((_ => (t => (f => f((nothing => nothing)))))))))((((((y => (F => F((x => ((y(y))(F))(x))))))((y => (F => F((x => ((y(y))(F))(x)))))))((mod => (m => (n => (((((x => (y => ((((n => (n((_ => (t => (f => f((nothing => nothing)))))))((t => (f => t((nothing => nothing)))))))((((n => (m => (m((n => (f => (z => ((n((g => (h => h(g(f))))))((u => z)))((u => u)))))))(n))))(x))(y)))((_ => ((n => (n((_ => (t => (f => f((nothing => nothing)))))))((t => (f => t((nothing => nothing)))))))((((n => (m => (m((n => (f => (z => ((n((g => (h => h(g(f))))))((u => z)))((u => u)))))))(n))))(y))(x)))))((_ => (t => (f => f((nothing => nothing)))))))))(n))((f => (z => z))))((_ => (_ => ((f => f(f)))((f => f(f)))))))((_ => (((((m => (n => ((n => (n((_ => (t => (f => f((nothing => nothing)))))))((t => (f => t((nothing => nothing)))))))((((n => (m => (m((n => (f => (z => ((n((g => (h => h(g(f))))))((u => z)))((u => u)))))))(n))))(m))(n)))))(n))(m))((_ => (mod((((n => (m => (m((n => (f => (z => ((n((g => (h => h(g(f))))))((u => z)))((u => u)))))))(n))))(m))(n)))(n))))((_ => m)))))))))(i))((f => (z => f(f(f(f(f(z))))))))))((f => (z => z))))((_ => (fizzbuzz((((n => (m => (f => (z => (m(f))((n(f))(z)))))))(i))((f => (z => f(z))))))((((car => (cdr => (on_cons => (on_nil => (on_cons(car))(cdr))))))((((car => (cdr => (on_cons => (on_nil => (on_cons(car))(cdr))))))((f => (z => f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(z))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))((((car => (cdr => (on_cons => (on_nil => (on_cons(car))(cdr))))))((f => (z => f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(z)))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))((((car => (cdr => (on_cons => (on_nil => (on_cons(car))(cdr))))))((f => (z => f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(z))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))((((car => (cdr => (on_cons => (on_nil => (on_cons(car))(cdr))))))((f => (z => f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(z))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))((on_cons => (on_nil => on_nil((nothing => nothing))))))))))(s)))))((_ => (fizzbuzz((((n => (m => (f => (z => (m(f))((n(f))(z)))))))(i))((f => (z => f(z))))))((((car => (cdr => (on_cons => (on_nil => (on_cons(car))(cdr))))))((((((m => (n => (((((m => (n => ((n => (n((_ => (t => (f => f((nothing => nothing)))))))((t => (f => t((nothing => nothing)))))))((((n => (m => (m((n => (f => (z => ((n((g => (h => h(g(f))))))((u => z)))((u => u)))))))(n))))(m))(n)))))(m))(n))((_ => (((x => (y => ((b => (b((_ => (t => (f => f((nothing => nothing)))))))((_ => (t => (f => t((nothing => nothing))))))))((((x => (y => ((((n => (n((_ => (t => (f => f((nothing => nothing)))))))((t => (f => t((nothing => nothing)))))))((((n => (m => (m((n => (f => (z => ((n((g => (h => h(g(f))))))((u => z)))((u => u)))))))(n))))(x))(y)))((_ => ((n => (n((_ => (t => (f => f((nothing => nothing)))))))((t => (f => t((nothing => nothing)))))))((((n => (m => (m((n => (f => (z => ((n((g => (h => h(g(f))))))((u => z)))((u => u)))))))(n))))(y))(x)))))((_ => (t => (f => f((nothing => nothing)))))))))(x))(y)))))(m))(n))))((_ => (t => (f => f((nothing => nothing)))))))))(i))((f => (z => f(f(f(f(f(f(f(f(f(f(z))))))))))))))((_ => (((car => (cdr => (on_cons => (on_nil => (on_cons(car))(cdr))))))((((n => (m => (f => (z => (m(f))((n(f))(z)))))))((f => (z => f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(z))))))))))))))))))))))))))))))))))))))))))))))))))))(i)))((on_cons => (on_nil => on_nil((nothing => nothing))))))))((_ => (((car => (cdr => (on_cons => (on_nil => (on_cons(car))(cdr))))))((((n => (m => (f => (z => (m(f))((n(f))(z)))))))((f => (z => f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(z))))))))))))))))))))))))))))))))))))))))))))))))))))((((((y => (F => F((x => ((y(y))(F))(x))))))((y => (F => F((x => ((y(y))(F))(x)))))))((div => (m => (n => (((((x => (y => ((((n => (n((_ => (t => (f => f((nothing => nothing)))))))((t => (f => t((nothing => nothing)))))))((((n => (m => (m((n => (f => (z => ((n((g => (h => h(g(f))))))((u => z)))((u => u)))))))(n))))(x))(y)))((_ => ((n => (n((_ => (t => (f => f((nothing => nothing)))))))((t => (f => t((nothing => nothing)))))))((((n => (m => (m((n => (f => (z => ((n((g => (h => h(g(f))))))((u => z)))((u => u)))))))(n))))(y))(x)))))((_ => (t => (f => f((nothing => nothing)))))))))(n))((f => (z => z))))((_ => (_ => ((f => f(f)))((f => f(f)))))))((_ => (((((m => (n => ((n => (n((_ => (t => (f => f((nothing => nothing)))))))((t => (f => t((nothing => nothing)))))))((((n => (m => (m((n => (f => (z => ((n((g => (h => h(g(f))))))((u => z)))((u => u)))))))(n))))(m))(n)))))(n))(m))((_ => (((n => (m => (f => (z => (m(f))((n(f))(z)))))))((f => (z => f(z)))))((div((((n => (m => (m((n => (f => (z => ((n((g => (h => h(g(f))))))((u => z)))((u => u)))))))(n))))(m))(n)))(n)))))((_ => (f => (z => z)))))))))))(i))((f => (z => f(f(f(f(f(f(f(f(f(f(z))))))))))))))))((((car => (cdr => (on_cons => (on_nil => (on_cons(car))(cdr))))))((((n => (m => (f => (z => (m(f))((n(f))(z)))))))((f => (z => f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(f(z))))))))))))))))))))))))))))))))))))))))))))))))))))((((((y => (F => F((x => ((y(y))(F))(x))))))((y => (F => F((x => ((y(y))(F))(x)))))))((mod => (m => (n => (((((x => (y => ((((n => (n((_ => (t => (f => f((nothing => nothing)))))))((t => (f => t((nothing => nothing)))))))((((n => (m => (m((n => (f => (z => ((n((g => (h => h(g(f))))))((u => z)))((u => u)))))))(n))))(x))(y)))((_ => ((n => (n((_ => (t => (f => f((nothing => nothing)))))))((t => (f => t((nothing => nothing)))))))((((n => (m => (m((n => (f => (z => ((n((g => (h => h(g(f))))))((u => z)))((u => u)))))))(n))))(y))(x)))))((_ => (t => (f => f((nothing => nothing)))))))))(n))((f => (z => z))))((_ => (_ => ((f => f(f)))((f => f(f)))))))((_ => (((((m => (n => ((n => (n((_ => (t => (f => f((nothing => nothing)))))))((t => (f => t((nothing => nothing)))))))((((n => (m => (m((n => (f => (z => ((n((g => (h => h(g(f))))))((u => z)))((u => u)))))))(n))))(m))(n)))))(n))(m))((_ => (mod((((n => (m => (m((n => (f => (z => ((n((g => (h => h(g(f))))))((u => z)))((u => u)))))))(n))))(m))(n)))(n))))((_ => m)))))))))(i))((f => (z => f(f(f(f(f(f(f(f(f(f(z))))))))))))))))((on_cons => (on_nil => on_nil((nothing => nothing))))))))))(s)))))))))))((_ => s)))))))```


```js
console.log(unchurchifyList(unchurchifyString, ... ))
```

> ```["Buzz",["Fizz",["98",["97",["Fizz",["Buzz",["94",["Fizz",["92",["91",["FizzBuzz",["89",["88",["Fizz",["86",["Buzz",["Fizz",["83",["82",["Fizz",["Buzz",["79",["Fizz",["77",["76",["FizzBuzz",["74",["73",["Fizz",["71",["Buzz",["Fizz",["68",["67",["Fizz",["Buzz",["64",["Fizz",["62",["61",["FizzBuzz",["59",["58",["Fizz",["56",["Buzz",["Fizz",["53",["52",["Fizz",["Buzz",["49",["Fizz",["47",["46",["FizzBuzz",["44",["43",["Fizz",["41",["Buzz",["Fizz",["38",["37",["Fizz",["Buzz",["34",["Fizz",["32",["31",["FizzBuzz",["29",["28",["Fizz",["26",["Buzz",["Fizz",["23",["22",["Fizz",["Buzz",["19",["Fizz",["17",["16",["FizzBuzz",["14",["13",["Fizz",["11",["Buzz",["Fizz",["8",["7",["Fizz",["Buzz",["4",["Fizz",["2",["1",null]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]```
