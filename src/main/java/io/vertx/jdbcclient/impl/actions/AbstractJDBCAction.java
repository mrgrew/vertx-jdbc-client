/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.jdbcclient.impl.actions;

import io.vertx.core.json.JsonArray;
import io.vertx.ext.sql.SQLOptions;
import io.vertx.ext.jdbc.spi.JDBCColumnDescriptorProvider;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;

/**
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 * @author <a href="mailto:plopes@redhat.com">Paulo Lopes</a>
 */
public abstract class AbstractJDBCAction<T> implements JDBCAction<T> {

  protected final SQLOptions options;
  protected final JDBCStatementHelper helper;
  private static final JsonArray EMPTY = new JsonArray(Collections.unmodifiableList(new ArrayList<>()));

  protected AbstractJDBCAction(SQLOptions options) {
    this(null, options);
  }

  protected AbstractJDBCAction(JDBCStatementHelper helper, SQLOptions options) {
    this.options = options;
    this.helper = helper;
  }

  public abstract T execute(Connection conn) throws SQLException;

  protected void applyStatementOptions(Statement statement) throws SQLException {
    if (options != null) {
      if (options.getQueryTimeout() > 0) {
        statement.setQueryTimeout(options.getQueryTimeout());
      }
      if (options.getFetchDirection() != null) {
        statement.setFetchDirection(options.getFetchDirection().getType());
      }
      if (options.getFetchSize() != 0) {
        statement.setFetchSize(options.getFetchSize());
      }
      if (options.getMaxRows() > 0) {
        statement.setMaxRows(options.getMaxRows());
      }
    }
  }

  protected void fillStatement(PreparedStatement statement, JsonArray in) throws SQLException {
    ParameterMetaData md = new CachedParameterMetaData(statement);
    JDBCColumnDescriptorProvider provider = JDBCColumnDescriptorProvider.fromParameterMetaData(md);
    fillStatement(statement, in, provider);
  }

  protected void fillStatement(PreparedStatement statement, JsonArray in, JDBCColumnDescriptorProvider provider) throws SQLException {
    if (in == null) {
      in = EMPTY;
    }

    for (int pos = 1; pos <= in.size(); pos++) {
      statement.setObject(pos, helper.getEncoder().encode(in, pos, provider));
    }
  }

  protected void fillStatement(CallableStatement statement, JsonArray in, JsonArray out, JDBCColumnDescriptorProvider provider) throws SQLException {
    if (in == null) {
      in = EMPTY;
    }

    if (out == null) {
      out = EMPTY;
    }

    int max = Math.max(in.size(), out.size());

    for (int i = 0; i < max; i++) {
      Object value;
      boolean set = false;

      if (i < in.size()) {
        value = helper.getEncoder().encode(in, i + 1, provider);
        if (value != null) {
          statement.setObject(i + 1, value);
          set = true;
        }
      }

      // reset
      value = null;

      if (i < out.size()) {
        value = out.getValue(i);
      }

      // found a out value, use it as a output parameter
      if (value != null) {
        // We're using the int from the enum instead of the enum itself to allow working with Drivers
        // that have not been upgraded to Java8 yet.
        if (value instanceof String) {
          statement.registerOutParameter(i + 1, JDBCType.valueOf((String) value).getVendorTypeNumber());
        } else if (value instanceof Number) {
          // for cases where vendors have special codes (e.g.: Oracle)
          statement.registerOutParameter(i + 1, ((Number) value).intValue());
        }
        set = true;
      }

      if (!set) {
        // assume null input
        statement.setNull(i + 1, Types.NULL);
      }
    }
  }
}
