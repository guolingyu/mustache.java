package com.sampullara.mustache;

import java.lang.reflect.AccessibleObject;
import java.util.Iterator;

/**
 * TODO: Edit this
 * <p/>
 * User: sam
 * Date: 7/24/11
 * Time: 2:59 PM
 */
public interface ObjectHandler {
  AccessibleObject getMember(String name, Class aClass);
  Object handleObject(Object parent, Scope scope, String name);
  Iterator iterate(Object object);
}
