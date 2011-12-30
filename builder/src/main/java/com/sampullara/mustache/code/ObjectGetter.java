package com.sampullara.mustache.code;

import com.sampullara.mustache.Scope;

/**
 * Created by IntelliJ IDEA.
 * User: spullara
 * Date: 12/29/11
 * Time: 5:44 PM
 * To change this template use File | Settings | File Templates.
 */
public class ObjectGetter {
  public static Object getObject(Scope scope) {
    Class parent = (Class) scope.getParent();
    return parent.getName();
  }
}
