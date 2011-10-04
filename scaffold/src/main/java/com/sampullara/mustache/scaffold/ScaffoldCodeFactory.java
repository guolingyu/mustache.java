package com.sampullara.mustache.scaffold;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.sampullara.mustache.Code;
import com.sampullara.mustache.CodeFactory;
import com.sampullara.mustache.Mustache;
import com.sampullara.mustache.MustacheException;
import com.sampullara.mustache.Scope;
import com.sampullara.util.FutureWriter;

/**
 * Generates Java backing code for a template
 */
public class ScaffoldCodeFactory implements CodeFactory {
  public static class Call {
    @Override
    public String toString() {
      return "Call{" +
              "type='" + type + '\'' +
              ", name='" + name + '\'' +
              '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Call call = (Call) o;

      if (name != null ? !name.equals(call.name) : call.name != null) return false;
      if (type != null ? !type.equals(call.type) : call.type != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = type != null ? type.hashCode() : 0;
      result = 31 * result + (name != null ? name.hashCode() : 0);
      return result;
    }

    public Call(String type, String name) {
      this.type = type;
      this.name = name;
    }
    public final String type;
    public final String name;
  }

  @Override
  public Code iterable(final Mustache m, final String variable, List<Code> codes, String file, int line) {
    return new MyCode(codes) {
      @Override
      public void execute(FutureWriter fw, Scope scope) throws MustacheException {
        execute(scope, new Call("iterable", variable));
      }
    };
  }

  @Override
  public Code function(final Mustache m, final String variable, List<Code> codes, String file, int line) {
    return new MyCode(codes) {
      @Override
      public void execute(FutureWriter fw, Scope scope) throws MustacheException {
        execute(scope, new Call("function", variable));
      }
    };
  }

  @Override
  public Code ifIterable(final Mustache m, final String variable, List<Code> codes, String file, int line) {
    return new MyCode(codes) {
      @Override
      public void execute(FutureWriter fw, Scope scope) throws MustacheException {
        execute(scope, new Call("if", variable));
      }
    };
  }

  @Override
  public Code notIterable(final Mustache m, final String variable, List<Code> codes, String file, int line) {
    return new MyCode(codes) {
      @Override
      public void execute(FutureWriter fw, Scope scope) throws MustacheException {
        execute(scope, new Call("not", variable));
      }
    };
  }

  @Override
  public Code partial(final Mustache m, final String variable, String file, int line) {
    return new MyCode() {
      @Override
      public void execute(FutureWriter fw, Scope scope) throws MustacheException {
        Scope subScope = new Scope();
        scope.put(variable, subScope);
        Mustache partial = m.partial(variable);
        partial.execute(fw, scope);
      }
    };
  }

  @Override
  public Code value(Mustache m, final String variable, boolean encode, int line) {
    return new MyCode() {
      @Override
      public void execute(FutureWriter fw, Scope scope) throws MustacheException {
        scope.put(new Call("value", variable), new Scope());
      }
    };
  }

  @Override
  public Code write(String s, int line) {
    return new NOP();
  }

  @Override
  public Code eof(int line) {
    return new NOP();
  }

  private static abstract class MyCode implements Code {

    private List<Code> codes;

    public MyCode() {

    }

    public MyCode(List<Code> codes) {
      this.codes = codes;
    }

    protected void execute(Scope scope, Call call) throws MustacheException {
      Scope subScope = new Scope();
      scope.put(call, subScope);
      for (Code code : codes) {
        code.execute(new FutureWriter(), subScope);
      }
    }

    @Override
    public int getLine() {
      return 0;
    }

    @Override
    public Scope unparse(Scope current, String text, AtomicInteger position, Code[] next) throws MustacheException {
      return null;
    }
  }

  private static class NOP extends MyCode {
    @Override
    public void execute(FutureWriter fw, Scope scope) throws MustacheException {
    }
  }
}
