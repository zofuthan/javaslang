/*     / \____  _    ______   _____ / \____   ____  _____
 *    /  \__  \/ \  / \__  \ /  __//  \__  \ /    \/ __  \   Javaslang
 *  _/  // _\  \  \/  / _\  \\_  \/  // _\  \  /\  \__/  /   Copyright 2014-2015 Daniel Dietrich
 * /___/ \_____/\____/\_____/____/\___\_____/_/  \_/____/    Licensed under the Apache License, Version 2.0
 */

import java.nio.charset.{Charset, StandardCharsets}
import java.nio.file.StandardOpenOption

import StringContextImplicits._

import scala.util.Properties.lineSeparator

val N = 13
val TARGET = "src-gen/main/java"

// entry point
def run() {

  genFunctions()
  genTuples()
}

def genFunctions(): Unit = {

  def genLambda(): String = xs"""
package javaslang.function;

import javaslang.control.Try;

import java.io.Serializable;
import java.lang.invoke.MethodType;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.util.function.Function;

/**
 * <p>
 * This is a general definition of a checked function of unknown parameters and a return value of type R.
 * A checked function may throw an exception. The exception type is not a generic type parameter because
 * when composing functions, we cannot say anything else about the resulting type of exception than that it is
 * a Throwable.
 * </p>
 * <p>
 * This class is intended to be used internally.
 * </p>
 *
 * @param <R> Return type of the checked function.
 */
public interface Lambda<R> extends Serializable {

    /**
     * Serializes a lambda and returns the corresponding {@link java.lang.invoke.SerializedLambda}.
     *
     * @param lambda A serializable lambda
     * @return The serialized lambda wrapped in a {@link javaslang.control.Try.Success}, or a {@link javaslang.control.Try.Failure}
     * if an exception occurred.
     * @see <a
     * href="http://stackoverflow.com/questions/21860875/printing-debug-info-on-errors-with-java-8-lambda-expressions">printing
     * debug info on errors with java 8 lambda expressions</a>
     * @see <a href="http://www.slideshare.net/hendersk/method-handles-in-java">Method Handles in Java</a>
     */
    static SerializedLambda getSerializedLambda(Serializable lambda) {
        return Try.of(() -> {
            final Method method = lambda.getClass().getDeclaredMethod("writeReplace");
            method.setAccessible(true);
            return (SerializedLambda) method.invoke(lambda);
        }).get();
    }

    /**
     * <p>
     * Gets the runtime method signature of the given lambda instance. Especially this function is handy when the
     * functional interface is generic and the parameter and/or return types cannot be determined directly.
     * </p>
     * <p>
     * Uses internally the {@link java.lang.invoke.SerializedLambda#getImplMethodSignature()} by parsing the JVM field
     * types of the method signature. The result is a {@link java.lang.invoke.MethodType} which contains the return type
     * and the parameter types of the given lambda.
     * </p>
     *
     * @param lambda A serializable lambda.
     * @return The signature of the lambda as {@linkplain java.lang.invoke.MethodType}.
     */
    static MethodType getLambdaSignature(Serializable lambda) {
        final String signature = getSerializedLambda(lambda).getImplMethodSignature();
        return MethodType.fromMethodDescriptorString(signature, lambda.getClass().getClassLoader());
    }

    /**
     * @return the numper of function arguments.
     * @see <a href="http://en.wikipedia.org/wiki/Arity">Arity</a>
     */
    int arity();

    /**
     * Returns a curried version of this function.
     *
     * @return A curried function equivalent to this.
     */
    Lambda curried();

    /**
     * Returns a tupled version of this function.
     *
     * @return A tupled function equivalent to this.
     */
    Lambda<R> tupled();

    /**
     * Returns a reversed version of this function.
     *
     * @return A reversed function equivalent to this.
     */
    Lambda<R> reversed();

    /**
     * There can be nothing said about the type of exception (in Java), if the Function arg is also a checked function.
     * In an ideal world we could denote the appropriate bound of both exception types (this and after).
     * This is the reason why CheckedFunction throws a Throwable instead of a concrete exception.
     *
     * @param after Functions applied after this
     * @param <V> Return value of after
     * @return A Function composed of this and after
     */
    <V> Lambda<V> andThen(Function<? super R, ? extends V> after);

    default MethodType getType() {
        return Lambda.getLambdaSignature(this);
    }
}"""

  def genFunctions(i: Int): Unit = {

    val generics = gen(1 to i)(j => s"T$j")(", ")
    val genericsReversed = gen((1 to i).reverse)(j => s"T$j")(", ")
    val genericsTuple = if (i > 0) s"<$generics>" else ""
    val genericsFunction = if (i > 0) s"$generics, " else ""
    val genericsReversedFunction = if (i > 0) s"$genericsReversed, " else ""
    val curried = if (i == 0) "v" else gen(1 to i)(j => s"t$j")(" -> ")
    val paramsDecl = gen(1 to i)(j => s"T$j t$j")(", ")
    val params = gen(1 to i)(j => s"t$j")(", ")
    val paramsReversed = gen((1 to i).reverse)(j => s"t$j")(", ")
    val tupled = gen(1 to i)(j => s"t._$j")(", ")

    def additionalInterfaces(arity: Int, checked: Boolean): String = (arity, checked) match {
      case (0, false) => s", java.util.function.Supplier<R>"
      case (1, false) => s", java.util.function.Function<$generics, R>"
      case (2, false) => s", java.util.function.BiFunction<$generics, R>"
      case _ => ""
    }

    def returnType(max: Int, function: String): String = {
      if (max == 0) {
          s"${function}1<Void, R>"
      } else {
          def returnType(curr: Int, max: Int): String = {
              val isParam = curr < max
              val next = if (isParam) returnType(curr + 1, max) else "R"
              s"${function}1<T$curr, $next>"
          }
          returnType(1, max)
      }
    }

    def genFunction(name: String, checked: Boolean): String = xs"""
    package javaslang.function;

    import javaslang.Tuple$i;

    import java.util.Objects;
    import java.util.function.Function;

    @FunctionalInterface
    public interface $name$i<${if (i > 0) s"$generics, " else ""}R> extends Lambda<R>${additionalInterfaces(i, checked)} {

        ${if (i == 1) xs"""
        static <T> ${name}1<T, T> identity() {
            return t -> t;
        }""" else ""}

        ${if ((i == 1 || i == 2) && !checked) "@Override" else ""}
        R apply($paramsDecl)${if (checked) " throws Throwable" else ""};

        ${if (i == 0 && !checked) xs"""
        @Override
        default R get() {
            return apply();
        }""" else ""}

        @Override
        default int arity() {
            return $i;
        }

        @Override
        default ${returnType(i, name)} curried() {
            return $curried -> apply($params);
        }

        @Override
        default ${name}1<Tuple$i$genericsTuple, R> tupled() {
            return t -> apply($tupled);
        }

        @Override
        default $name$i<${genericsReversedFunction}R> reversed() {
            return ($paramsReversed) -> apply($params);
        }

        @Override
        default <V> $name$i<${genericsFunction}V> andThen(Function<? super R, ? extends V> after) {
            Objects.requireNonNull(after);
            return ($params) -> after.apply(apply($params));
        }

        ${if (i == 1) xs"""
        default <V> ${name}1<V, R> compose(Function<? super V, ? extends T1> before) {
            Objects.requireNonNull(before);
            return v -> apply(before.apply(v));
        }""" else ""}
    }
    """

    genJavaFile("javaslang/function", s"χ$i.java")(() => genFunction("χ", checked = true))
    genJavaFile("javaslang/function", s"CheckedLambda$i.java")(() => genFunction("CheckedLambda", checked = true))
    genJavaFile("javaslang/function", s"λ$i.java")(() => genFunction("λ", checked = false))
    genJavaFile("javaslang/function", s"Lambda$i.java")(() => genFunction("Lambda", checked = false))
  }

  genJavaFile("javaslang/function", "Lambda.java")(genLambda)

  (0 to N).foreach(genFunctions)
}

def genTuples(): Unit = {

  def genFactoryMethod(i: Int) = {
    val generics = gen(1 to i)(j => s"T$j")(", ")
    val paramsDecl = gen(1 to i)(j => s"T$j t$j")(", ")
    val params = gen(1 to i)(j => s"t$j")(", ")
    xs"""
    static <$generics> Tuple$i<$generics> of($paramsDecl) {
        return new Tuple$i<>($params);
    }
    """
  }

  def genTuple0(): String = xs"""
    package javaslang;

    import java.util.Objects;

    /**
     * Implementation of an empty tuple, a tuple containing no elements.
     */
    public final class Tuple0 implements Tuple {

        private static final long serialVersionUID = 1L;

        /**
         * The singleton instance of Tuple0.
         */
        private static final Tuple0 INSTANCE = new Tuple0();

        /**
         * Hidden constructor.
         */
        private Tuple0() {
        }

        /**
         * Returns the singleton instance of Tuple0.
         *
         * @return The singleton instance of Tuple0.
         */
        public static Tuple0 instance() {
            return INSTANCE;
        }

        @Override
        public int arity() {
            return 0;
        }

        @Override
        public Tuple0 unapply() {
            return this;
        }

        @Override
        public boolean equals(Object o) {
            return o == this;
        }

        @Override
        public int hashCode() {
            return Objects.hash();
        }

        @Override
        public String toString() {
            return "()";
        }

        // -- Serializable implementation

        /**
         * Instance control for object serialization.
         *
         * @return The singleton instance of Tuple0.
         * @see java.io.Serializable
         */
        private Object readResolve() {
            return INSTANCE;
        }
    }
  """

  def genTuple(i: Int): Unit = {
    val generics = gen(1 to i)(j => s"T$j")(", ")
    val paramsDecl = gen(1 to i)(j => s"T$j t$j")(", ")
    val tuple = xs"""
    package javaslang;

    import java.util.Objects;

    /**
     * Implementation of a pair, a tuple containing $i elements.
     */
    public class Tuple$i<$generics> implements Tuple {

        private static final long serialVersionUID = 1L;

        ${gen(1 to i)(j => s"public final T$j _$j;")("\n")}

        public Tuple$i($paramsDecl) {
            ${gen(1 to i)(j => s"this._$j = t$j;")("\n")}
        }

        @Override
        public int arity() {
            return $i;
        }

        @Override
        public Tuple$i<$generics> unapply() {
            return this;
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof Tuple$i)) {
                return false;
            } else {
                final Tuple$i that = (Tuple$i) o;
                return ${gen(1 to i)(j => s"Objects.equals(this._$j, that._$j)")("\n                         && ")};
            }
        }

        @Override
        public int hashCode() {
            return Objects.hash(${gen(1 to i)(j => s"_$j")(", ")});
        }

        @Override
        public String toString() {
            return String.format("(${gen(1 to i)(_ => s"%s")(", ")})", ${gen(1 to i)(j => s"_$j")(", ")});
        }
    }
    """

    genJavaFile("javaslang", s"Tuple$i.java")(() => tuple)
  }

  def genBaseTuple(): String = xs"""
  package javaslang;

  public interface Tuple extends ValueObject {

      /**
       * Returns the number of elements of this tuple.
       *
       * @return The number of elements.
       */
      int arity();

      // -- factory methods

      static Tuple0 empty() {
          return Tuple0.instance();
      }

      ${gen(1 to N)(genFactoryMethod)("\n\n")}
  }
  """

  genJavaFile("javaslang", "Tuple.java")(genBaseTuple)
  genJavaFile("javaslang", "Tuple0.java")(genTuple0)

  (1 to N).foreach(genTuple)
}

/**
 * Generates a Java file.
 * @param pkg A path of the java package
 * @param fileName A file name, may contain path segments.
 * @param gen A generator which produces a String.
 */
def genJavaFile(pkg: String, fileName: String)(gen: () => String)(implicit charset: Charset = StandardCharsets.UTF_8): Unit = {

  println(s"Generating $pkg/$fileName")

  val fileContents = xs"""
    ${classHeader()}
    ${gen.apply()}
  """

  import java.nio.file.{Paths, Files}

  Files.write(
    Files.createDirectories(Paths.get(TARGET, pkg)).resolve(fileName),
    fileContents.getBytes(charset),
    StandardOpenOption.CREATE, StandardOpenOption.WRITE)
}

/**
 * Applies f for a range of Ints using delimiter to mkString the output.
 * @param range A range of Ints
 * @param f A generator which takes an Int and produces a String
 * @param delimiter The delimiter of the strings parts
 * @return Generated String
 */
def gen(range: Range)(f: Int => String)(implicit delimiter: String = "") = range.map(i => f.apply(i)) mkString delimiter

/**
 * The header for Java files.
 * @return A header as String
 */
def classHeader() = xs"""
  /**    / \\____  _    ______   _____ / \\____   ____  _____
   *    /  \\__  \\/ \\  / \\__  \\ /  __//  \\__  \\ /    \\/ __  \\   Javaslang
   *  _/  // _\\  \\  \\/  / _\\  \\\\_  \\/  // _\\  \\  /\\  \\__/  /   Copyright 2014-2015 Daniel Dietrich
   * /___/ \\_____/\\____/\\_____/____/\\___\\_____/_/  \\_/____/    Licensed under the Apache License, Version 2.0
   */
  // @@ GENERATED FILE - DO NOT MODIFY @@
  """

/**
 * Indentation of cascaded rich strings.
 * @see https://gist.github.com/danieldietrich/5174348
 */
object StringContextImplicits {

  implicit class StringContextExtension(sc: StringContext) {

    def xs(args: Any*): String = align(sc.s, args)

    def xraw(args: Any*): String = align(sc.raw, args)

    /**
     * Indenting a rich string, removing first and last newline.
     * A rich string consists of arguments surrounded by text parts.
     */
    private def align(interpolator: Seq[Any] => String, args: Seq[Any]) = {

      // indent embedded strings, invariant: parts.length = args.length + 1
      val indentedArgs = for {
        (part, arg) <- sc.parts zip args.map(s => if (s == null) "" else s.toString)
      } yield {
        // get the leading space of last line of current part
        val space = """([ \t]*)[^\s]*$""".r.findFirstMatchIn(part).map(_.group(1)).getOrElse("")
        // add this leading space to each line (except the first) of current arg
        arg.split("\r?\n") match {
            case lines: Array[String] if lines.length > 0 => lines reduce (_ + lineSeparator + space + _)
            case whitespace => whitespace mkString ""
        }
      }

      // remove first and last newline and split string into separate lines
      // adding termination symbol \u0000 in order to preserve empty strings between last newlines when splitting
      val split = (interpolator(indentedArgs).replaceAll( """(^[ \t]*\r?\n)|(\r?\n[ \t]*$)""", "") + '\u0000').split("\r?\n")

      // find smallest indentation
      val prefix = split filter (!_.trim().isEmpty) map { s =>
        """^\s+""".r.findFirstIn(s).getOrElse("")
      } match {
        case prefixes: Array[String] if prefixes.length > 0 => prefixes reduce { (s1, s2) =>
          if (s1.length <= s2.length) s1 else s2
        }
        case _ => ""
      }

      // align all lines
      val aligned = split map { s =>
        if (s.startsWith(prefix)) s.substring(prefix.length) else s
      } mkString lineSeparator dropRight 1 // dropping termination character \u0000

      // combine multiple newlines to two
      aligned.replaceAll("""[ \t]*\r?\n ([ \t]*\r?\n)+""", lineSeparator * 2)
    }
  }

}