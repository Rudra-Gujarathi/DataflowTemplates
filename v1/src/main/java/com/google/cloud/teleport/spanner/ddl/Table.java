/*
 * Copyright (C) 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.cloud.teleport.spanner.ddl;

import static com.google.cloud.teleport.spanner.common.NameUtils.quoteIdentifier;

import com.google.auto.value.AutoValue;
import com.google.cloud.spanner.Dialect;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import java.io.IOException;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Cloud Spanner table. */
@AutoValue
public abstract class Table implements Serializable {

  private static final long serialVersionUID = 1295819360440139056L;

  @Nullable
  public abstract String name();

  @Nullable
  public abstract String interleaveInParent();

  public enum InterleaveType {
    IN,
    IN_PARENT
  };

  @Nullable
  public abstract InterleaveType interleaveType();

  public abstract ImmutableList<IndexColumn> primaryKeys();

  public abstract boolean onDeleteCascade();

  public abstract ImmutableList<Column> columns();

  public abstract ImmutableList<String> indexes();

  public abstract ImmutableList<String> foreignKeys();

  public abstract ImmutableList<String> checkConstraints();

  public abstract Builder autoToBuilder();

  public abstract Dialect dialect();

  public Builder toBuilder() {
    Builder builder = autoToBuilder();
    builder = builder.dialect(dialect());
    for (Column column : columns()) {
      builder.addColumn(column);
    }
    for (IndexColumn pk : primaryKeys()) {
      builder.primaryKeyBuilder().set(pk);
    }
    return builder;
  }

  public static Builder builder(Dialect dialect) {
    return new AutoValue_Table.Builder()
        .dialect(dialect)
        .indexes(ImmutableList.of())
        .foreignKeys(ImmutableList.of())
        .checkConstraints(ImmutableList.of())
        .onDeleteCascade(false);
  }

  public static Builder builder() {
    return builder(Dialect.GOOGLE_STANDARD_SQL);
  }

  public void prettyPrint(Appendable appendable, boolean includeIndexes, boolean includeForeignKeys)
      throws IOException {
    switch (dialect()) {
      case GOOGLE_STANDARD_SQL:
        prettyPrintGsql(appendable, includeIndexes, includeForeignKeys);
        break;
      case POSTGRESQL:
        prettyPrintPg(appendable, includeIndexes, includeForeignKeys);
        break;
      default:
        throw new IllegalArgumentException(String.format("Unrecognized Dialect: %s", dialect()));
    }
  }

  private void prettyPrintPg(
      Appendable appendable, boolean includeIndexes, boolean includeForeignKeys)
      throws IOException {
    appendable.append("CREATE TABLE ").append(quoteIdentifier(name(), dialect())).append(" (");
    for (Column column : columns()) {
      appendable.append("\n\t");
      column.prettyPrint(appendable);
      appendable.append(",");
    }
    for (String checkConstraint : checkConstraints()) {
      appendable.append("\n\t");
      appendable.append(checkConstraint);
      appendable.append(",");
    }
    if (primaryKeys() != null) {
      appendable.append(
          primaryKeys().stream()
              .map(c -> quoteIdentifier(c.name(), dialect()))
              .collect(Collectors.joining(", ", "\n\tPRIMARY KEY (", ")")));
    }
    appendable.append("\n)");
    if (interleaveInParent() != null) {
      appendable
          .append(" \nINTERLEAVE IN ")
          .append(interleaveType() == InterleaveType.IN ? "" : "PARENT ")
          .append(quoteIdentifier(interleaveInParent(), dialect()));
      if (onDeleteCascade()) {
        appendable.append(" ON DELETE CASCADE");
      }
    }
    if (includeIndexes) {
      appendable.append("\n");
      appendable.append(String.join("\n", indexes()));
    }
    if (includeForeignKeys) {
      appendable.append("\n");
      appendable.append(String.join("\n", foreignKeys()));
    }
  }

  private void prettyPrintGsql(
      Appendable appendable, boolean includeIndexes, boolean includeForeignKeys)
      throws IOException {
    appendable.append("CREATE TABLE ").append(quoteIdentifier(name(), dialect())).append(" (");
    for (Column column : columns()) {
      appendable.append("\n\t");
      column.prettyPrint(appendable);
      appendable.append(",");
    }
    for (String checkConstraint : checkConstraints()) {
      appendable.append("\n\t");
      appendable.append(checkConstraint);
      appendable.append(",");
    }
    if (primaryKeys() != null) {
      appendable.append(
          primaryKeys().stream()
              .map(IndexColumn::toString)
              .collect(Collectors.joining(", ", "\n) PRIMARY KEY (", "")));
    }
    appendable.append(")");
    if (interleaveInParent() != null) {
      appendable
          .append(",\nINTERLEAVE IN ")
          .append(interleaveType() == InterleaveType.IN ? " " : "PARENT ")
          .append(quoteIdentifier(interleaveInParent(), dialect()));
      if (onDeleteCascade()) {
        appendable.append(" ON DELETE CASCADE");
      }
    }
    if (includeIndexes) {
      appendable.append("\n");
      appendable.append(String.join("\n", indexes()));
    }
    if (includeForeignKeys) {
      appendable.append("\n");
      appendable.append(String.join("\n", foreignKeys()));
    }
  }

  public String prettyPrint() {
    StringBuilder sb = new StringBuilder();
    try {
      prettyPrint(sb, true, true);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return sb.toString();
  }

  @Override
  public String toString() {
    return prettyPrint();
  }

  /** A builder for {@link Table}. */
  @AutoValue.Builder
  public abstract static class Builder {

    private Ddl.Builder ddlBuilder;
    private IndexColumn.IndexColumnsBuilder<Builder> primaryKeyBuilder;
    private LinkedHashMap<String, Column> columns = Maps.newLinkedHashMap();

    Builder ddlBuilder(Ddl.Builder ddlBuilder) {
      this.ddlBuilder = ddlBuilder;
      return this;
    }

    public abstract Builder name(String name);

    public abstract String name();

    public abstract Builder interleaveInParent(String parent);

    public abstract Builder interleaveType(Table.InterleaveType type);

    abstract Builder primaryKeys(ImmutableList<IndexColumn> value);

    abstract Builder onDeleteCascade(boolean onDeleteCascade);

    abstract Builder columns(ImmutableList<Column> columns);

    public abstract Builder indexes(ImmutableList<String> indexes);

    public abstract Builder foreignKeys(ImmutableList<String> foreignKeys);

    public abstract Builder checkConstraints(ImmutableList<String> checkConstraints);

    abstract Builder dialect(Dialect dialect);

    public abstract Dialect dialect();

    abstract ImmutableList<Column> columns();

    public IndexColumn.IndexColumnsBuilder<Builder> primaryKey() {
      return primaryKeyBuilder();
    }

    public Column.Builder column(String name) {
      Column column = columns.get(name.toLowerCase());
      if (column != null) {
        return column.toBuilder().tableBuilder(this);
      }
      return Column.builder(dialect()).name(name).tableBuilder(this);
    }

    public Builder addColumn(Column column) {
      columns.put(column.name().toLowerCase(), column);
      return this;
    }

    public Builder onDeleteCascade() {
      onDeleteCascade(true);
      return this;
    }

    abstract Table autoBuild();

    public Table build() {
      return primaryKeys(primaryKeyBuilder().build())
          .columns(ImmutableList.copyOf(columns.values()))
          .autoBuild();
    }

    public Ddl.Builder endTable() {
      ddlBuilder.addTable(build());
      return ddlBuilder;
    }

    private IndexColumn.IndexColumnsBuilder<Builder> primaryKeyBuilder() {
      if (primaryKeyBuilder == null) {
        primaryKeyBuilder = new IndexColumn.IndexColumnsBuilder<>(this, dialect());
      }
      return primaryKeyBuilder;
    }
  }

  public Column column(String name) {
    for (Column c : columns()) {
      if (c.name().equalsIgnoreCase(name)) {
        return c;
      }
    }
    return null;
  }
}
