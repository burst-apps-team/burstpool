/*
 * This file is generated by jOOQ.
 */
package burst.pool.db.tables;


import burst.pool.db.DefaultSchema;
import burst.pool.db.Indexes;
import burst.pool.db.Keys;
import burst.pool.db.tables.records.BestSubmissionsRecord;

import java.util.Arrays;
import java.util.List;

import javax.annotation.Generated;

import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Identity;
import org.jooq.Index;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Schema;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.TableImpl;


/**
 * This class is generated by jOOQ.
 */
@Generated(
    value = {
        "http://www.jooq.org",
        "jOOQ version:3.11.9"
    },
    comments = "This class is generated by jOOQ"
)
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class BestSubmissions extends TableImpl<BestSubmissionsRecord> {

    private static final long serialVersionUID = -1005134770;

    /**
     * The reference instance of <code>best_submissions</code>
     */
    public static final BestSubmissions BEST_SUBMISSIONS = new BestSubmissions();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<BestSubmissionsRecord> getRecordType() {
        return BestSubmissionsRecord.class;
    }

    /**
     * The column <code>best_submissions.db_id</code>.
     */
    public final TableField<BestSubmissionsRecord, Long> DB_ID = createField("db_id", org.jooq.impl.SQLDataType.BIGINT.nullable(false).identity(true), this, "");

    /**
     * The column <code>best_submissions.height</code>.
     */
    public final TableField<BestSubmissionsRecord, Long> HEIGHT = createField("height", org.jooq.impl.SQLDataType.BIGINT.defaultValue(org.jooq.impl.DSL.field("NULL", org.jooq.impl.SQLDataType.BIGINT)), this, "");

    /**
     * The column <code>best_submissions.account_id</code>.
     */
    public final TableField<BestSubmissionsRecord, Long> ACCOUNT_ID = createField("account_id", org.jooq.impl.SQLDataType.BIGINT.defaultValue(org.jooq.impl.DSL.field("NULL", org.jooq.impl.SQLDataType.BIGINT)), this, "");

    /**
     * The column <code>best_submissions.nonce</code>.
     */
    public final TableField<BestSubmissionsRecord, String> NONCE = createField("nonce", org.jooq.impl.SQLDataType.CLOB.defaultValue(org.jooq.impl.DSL.field("NULL", org.jooq.impl.SQLDataType.CLOB)), this, "");

    /**
     * The column <code>best_submissions.deadline</code>.
     */
    public final TableField<BestSubmissionsRecord, Long> DEADLINE = createField("deadline", org.jooq.impl.SQLDataType.BIGINT.defaultValue(org.jooq.impl.DSL.field("NULL", org.jooq.impl.SQLDataType.BIGINT)), this, "");

    /**
     * Create a <code>best_submissions</code> table reference
     */
    public BestSubmissions() {
        this(DSL.name("best_submissions"), null);
    }

    /**
     * Create an aliased <code>best_submissions</code> table reference
     */
    public BestSubmissions(String alias) {
        this(DSL.name(alias), BEST_SUBMISSIONS);
    }

    /**
     * Create an aliased <code>best_submissions</code> table reference
     */
    public BestSubmissions(Name alias) {
        this(alias, BEST_SUBMISSIONS);
    }

    private BestSubmissions(Name alias, Table<BestSubmissionsRecord> aliased) {
        this(alias, aliased, null);
    }

    private BestSubmissions(Name alias, Table<BestSubmissionsRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment(""));
    }

    public <O extends Record> BestSubmissions(Table<O> child, ForeignKey<O, BestSubmissionsRecord> key) {
        super(child, key, BEST_SUBMISSIONS);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Schema getSchema() {
        return DefaultSchema.DEFAULT_SCHEMA;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Index> getIndexes() {
        return Arrays.<Index>asList(Indexes.BEST_SUBMISSIONS_PRIMARY);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Identity<BestSubmissionsRecord, Long> getIdentity() {
        return Keys.IDENTITY_BEST_SUBMISSIONS;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UniqueKey<BestSubmissionsRecord> getPrimaryKey() {
        return Keys.KEY_BEST_SUBMISSIONS_PRIMARY;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<UniqueKey<BestSubmissionsRecord>> getKeys() {
        return Arrays.<UniqueKey<BestSubmissionsRecord>>asList(Keys.KEY_BEST_SUBMISSIONS_PRIMARY);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BestSubmissions as(String alias) {
        return new BestSubmissions(DSL.name(alias), this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BestSubmissions as(Name alias) {
        return new BestSubmissions(alias, this);
    }

    /**
     * Rename this table
     */
    @Override
    public BestSubmissions rename(String name) {
        return new BestSubmissions(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public BestSubmissions rename(Name name) {
        return new BestSubmissions(name, null);
    }
}
