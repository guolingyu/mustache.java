package com.sampullara.mustache.scaffold;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.sampullara.mustache.Code;
import com.sampullara.mustache.CodeFactory;
import com.sampullara.mustache.IdentityScope;
import com.sampullara.mustache.Mustache;
import com.sampullara.mustache.MustacheException;
import com.sampullara.mustache.Scope;
import com.sampullara.util.FutureWriter;

import static com.sampullara.mustache.IdentityScope.one;

/**
 * Generates Java backing code for a template
 */
public class JavaCodeFactory implements CodeFactory {
  @Override
  public Code iterable(final Mustache m, final String variable, List<Code> codes, String file, int line) {
    return new MyCode(codes) {
      @Override
      public void execute(FutureWriter fw, Scope scope) throws MustacheException {
        System.out.println("start iterable: " + variable);
        execute(m, scope);
        System.out.println("end   iterable: " + variable);
      }
    };
  }

  @Override
  public Code function(final Mustache m, final String variable, List<Code> codes, String file, int line) {
    return new MyCode(codes) {
      @Override
      public void execute(FutureWriter fw, Scope scope) throws MustacheException {
        System.out.println("start function: " + variable);
        execute(m, scope);
        System.out.println("end   function: " + variable);
      }
    };
  }

  @Override
  public Code ifIterable(final Mustache m, final String variable, List<Code> codes, String file, int line) {
    return new MyCode(codes) {
      @Override
      public void execute(FutureWriter fw, Scope scope) throws MustacheException {
        System.out.println("start if: " + variable);
        execute(m, scope);
        System.out.println("end   if: " + variable);
      }
    };
  }

  @Override
  public Code notIterable(final Mustache m, final String variable, List<Code> codes, String file, int line) {
    return new MyCode(codes) {
      @Override
      public void execute(FutureWriter fw, Scope scope) throws MustacheException {
        System.out.println("start not: " + variable);
        execute(m, scope);
        System.out.println("end   not: " + variable);
      }
    };
  }

  @Override
  public Code partial(final Mustache m, final String variable, String file, int line) {
    return new MyCode() {
      @Override
      public void execute(FutureWriter fw, Scope scope) throws MustacheException {
        System.out.println("partial: " + variable);
      }
    };
  }

  @Override
  public Code value(Mustache m, final String variable, boolean encode, int line) {
    return new MyCode() {
      @Override
      public void execute(FutureWriter fw, Scope scope) throws MustacheException {
        System.out.println("value: " + variable);
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

    protected void execute(Mustache m, Scope scope) throws MustacheException {
      for (Code code : codes) {
        code.execute(new FutureWriter(), scope);
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
