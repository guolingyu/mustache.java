package com.sampullara.mustache.code;

import com.sampullara.mustache.Mustache;
import com.sampullara.mustache.Scope;

/**
* Created by IntelliJ IDEA.
* User: spullara
* Date: 12/29/11
* Time: 1:37 PM
* To change this template use File | Settings | File Templates.
*/
public class DefaultWriteValueCode extends WriteValueCode {
  public DefaultWriteValueCode(Mustache m, String name, boolean encoded, int line) {
    super(m, name, encoded, line);
  }

  @Override
  protected Object getValue(Scope scope) {
    return m.getValue(scope, name);
  }
}
