/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.solr.common.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.noggit.JSONParser;
import org.noggit.ObjectBuilder;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableSet;

public class ValidatingJsonMap implements Map<String, Object> {

  public static final PredicateWithErrMsg<Object> NOT_NULL = o -> {
    if (o == null) return " Must not be NULL";
    return null;
  };
  public static final PredicateWithErrMsg<Pair> ENUM_OF = pair -> {
    if (pair.second() instanceof Set) {
      Set set = (Set) pair.second();
      if (pair.first() instanceof Collection) {
        for (Object o : (Collection) pair.first()) {
          if (!set.contains(o)) {
            return " Must be one of " + pair.second();
          }
        }
      } else {
        if (!set.contains(pair.first())) return " Must be one of " + pair.second() + ", got " + pair.first();
      }
      return null;
    } else {
      return " Unknown type";
    }

  };
  private final Map<String, Object> delegate;

  public ValidatingJsonMap(Map<String, Object> delegate) {
    this.delegate = delegate;
  }

  public ValidatingJsonMap(int i) {
    delegate = new LinkedHashMap<>(i);
  }

  public ValidatingJsonMap() {
    delegate = new LinkedHashMap<>();
  }

  @Override
  public int size() {
    return delegate.size();
  }

  @Override
  public boolean isEmpty() {
    return delegate.isEmpty();
  }

  @Override
  public boolean containsKey(Object key) {
    return delegate.containsKey(key);
  }

  @Override
  public boolean containsValue(Object value) {
    return delegate.containsValue(value);
  }

  @Override
  public Object get(Object key) {
    return delegate.get(key);
  }

  @Override
  public Object put(String key, Object value) {
    return delegate.put(key, value);
  }

  @Override
  public Object remove(Object key) {
    return null;
  }

  @Override
  public void putAll(Map<? extends String, ?> m) {
    delegate.putAll(m);
  }

  @Override
  public void clear() {
    delegate.clear();

  }

  @Override
  public Set<String> keySet() {
    return delegate.keySet();
  }

  @Override
  public Collection<Object> values() {
    return delegate.values();
  }

  @Override
  public Set<Entry<String, Object>> entrySet() {
    return delegate.entrySet();
  }

  public Object get(String key, PredicateWithErrMsg predicate) {
    Object v = get(key);
    if (predicate != null) {
      String msg = predicate.test(v);
      if (msg != null) {
        throw new RuntimeException("" + key + msg);
      }
    }
    return v;
  }

  public Boolean getBool(String key, Boolean def) {
    Object v = get(key);
    if (v == null) return def;
    if (v instanceof Boolean) return (Boolean) v;
    try {
      return Boolean.parseBoolean(v.toString());
    } catch (NumberFormatException e) {
      throw new RuntimeException("value of " + key + "must be an boolean");
    }
  }

  public Integer getInt(String key, Integer def) {
    Object v = get(key);
    if (v == null) return def;
    if (v instanceof Integer) return (Integer) v;
    try {
      return Integer.parseInt(v.toString());
    } catch (NumberFormatException e) {
      throw new RuntimeException("value of " + key + "must be an integer");
    }
  }
  public ValidatingJsonMap getMap(String key){
    return getMap(key,null,null);
  }
  public ValidatingJsonMap getMap(String key, PredicateWithErrMsg predicate) {
    return getMap(key, predicate, null);

  }

  public ValidatingJsonMap getMap(String key, PredicateWithErrMsg predicate, String message) {
    Object v = get(key);
    if (v != null && !(v instanceof Map)) {
      throw new RuntimeException("" + key + " should be of type map");
    }

    if (predicate != null) {
      String msg = predicate.test(v);
      if (msg != null) {
        msg = message != null ? message : key + msg;
        throw new RuntimeException(msg);
      }
    }
    return wrap((Map) v);
  }

  public List getList(String key, PredicateWithErrMsg predicate) {
    return getList(key, predicate, null);
  }

  public List getList(String key, PredicateWithErrMsg predicate, Object test) {
    Object v = get(key);
    if (v != null && !(v instanceof List)) {
      throw new RuntimeException("" + key + " should be of type List");
    }

    if (predicate != null) {
      String msg = predicate.test(test == null ? v : new Pair(v, test));
      if (msg != null) {
        throw new RuntimeException("" + key + msg);
      }
    }

    return (List) v;
  }

  public Object get(String key, PredicateWithErrMsg<Pair> predicate, Object arg) {
    Object v = get(key);
    String test = predicate.test(new Pair(v, arg));
    if (test != null) {
      throw new RuntimeException("" + key + test);
    }
    return v;
  }

  public Object get(String k, Object def) {
    Object v = get(k);
    if (v == null) return def;
    return v;
  }

  static ValidatingJsonMap wrap(Map<String, Object> map) {
    if (map == null) return null;
    if (map instanceof ValidatingJsonMap) {
      return (ValidatingJsonMap) map;
    } else {
      return new ValidatingJsonMap(map);
    }

  }

  public static ValidatingJsonMap fromJSON(InputStream is) {
    return fromJSON(new InputStreamReader(is, UTF_8));
  }

  public static ValidatingJsonMap fromJSON(Reader s) {
    try {
      return (ValidatingJsonMap) (getObjectBuilder(new JSONParser(s)).getObject());
    } catch (IOException e) {
      throw new RuntimeException();
    }
  }

  public static ValidatingJsonMap getDeepCopy(Map map, int maxDepth, boolean mutable) {
    if (map == null) return null;
    if (maxDepth < 1) return ValidatingJsonMap.wrap(map);
    ValidatingJsonMap copy = mutable ? new ValidatingJsonMap(map.size()) : new ValidatingJsonMap();
    for (Object o : map.entrySet()) {
      Map.Entry<String,Object> e = (Entry<String, Object>) o;
      Object v = e.getValue();
      if (v instanceof Map) v = getDeepCopy((Map) v, maxDepth - 1, mutable);
      else if (v instanceof Collection) v = getDeepCopy((Collection) v, maxDepth - 1, mutable);
      copy.put(e.getKey(), v);
    }
    return mutable ? copy : new ValidatingJsonMap(Collections.unmodifiableMap(copy));
  }

  public static Collection getDeepCopy(Collection c, int maxDepth, boolean mutable) {
    if (c == null || maxDepth < 1) return c;
    Collection result = c instanceof Set ? new HashSet() : new ArrayList();
    for (Object o : c) {
      if (o instanceof Map) {
        o = getDeepCopy((Map) o, maxDepth - 1, mutable);
      }
      result.add(o);
    }
    return mutable ? result : result instanceof Set ? unmodifiableSet((Set) result) : unmodifiableList((List) result);
  }

  private static ObjectBuilder getObjectBuilder(final JSONParser jp) throws IOException {
    return new ObjectBuilder(jp) {
      @Override
      public Object newObject() throws IOException {
        return new ValidatingJsonMap();
      }
    };
  }


  @Override
  public boolean equals(Object that) {
    return that instanceof Map && this.delegate.equals(that);
  }

  public static final ValidatingJsonMap EMPTY = new ValidatingJsonMap(Collections.EMPTY_MAP);
}