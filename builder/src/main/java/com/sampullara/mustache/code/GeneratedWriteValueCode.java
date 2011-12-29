package com.sampullara.mustache.code;

import com.sampullara.mustache.Mustache;
import com.sampullara.mustache.Scope;

public class GeneratedWriteValueCode extends WriteValueCode {
  public GeneratedWriteValueCode(Mustache m, String name, boolean encoded, int line) {
    super(m, name, encoded, line);
  }

  @Override
  protected Object getValue(Scope scope) {
    return m.getValue(scope, name);
  }
}
