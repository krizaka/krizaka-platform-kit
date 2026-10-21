/**
 * The tables of the kit's JDBC stores, created and migrated by Flyway at startup.
 *
 * <p>Each module ships its schema ({@code schema-postgresql.sql}); this module runs the ones a
 * service declared, in their own history table, so they never mix with the service's migrations.
 */
package com.krizaka.persistence;
