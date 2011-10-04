package com.sampullara.mustache;

/**
 * Interface for discovering values from scopes from a name
 */
public interface ObjectHandler {
  Object handleObject(Object parent, Scope scope, String name);
}
