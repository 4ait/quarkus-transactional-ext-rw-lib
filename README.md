# Quarkus Transactional RW Extensions

This library provides additional extensions for the Quarkus Transactional Read-Write Library, offering enhanced transaction management capabilities for Hibernate and serializable transactions in Quarkus applications.

## Features

- Hibernate-specific read-only transaction optimization
- Serializable isolation level support for read-only transactions
- Seamless integration with the base Quarkus Transactional Read-Write Library

## Installation

Add the following dependency to your project:

```xml
<dependency>
  <groupId>ru.code4a</groupId>
  <artifactId>quarkus-transactional-ext-rw-lib</artifactId>
  <version>[VERSION]</version>
</dependency>
```

Replace `[VERSION]` with the latest version of the library.

## Extensions

### 1. Hibernate Read-Only Transaction Processor

The `HibernateNewReadTransactionalRWProcessor` optimizes read-only transactions for Hibernate by:

- Setting the session to read-only mode
- Configuring the flush mode to MANUAL for improved efficiency

To enable this processor, set the following property in your `application.properties`:

```properties
ru.code4a.transactional-rw.hibernate.enabled=true
```

### 2. Serializable Read-Only Transaction Processor

The `SerializableNewReadTransactionalRWProcessor` sets the transaction isolation level to SERIALIZABLE for read-only transactions, providing the highest level of isolation.

To enable this processor, set the following property in your `application.properties`:

```properties
ru.code4a.transactional-rw.serializable.enabled=true
```

## Usage

Once the library is added to your project and the desired processors are enabled, they will automatically integrate with the base Quarkus Transactional Read-Write Library. The processors will be applied to read-only transactions based on their priorities.

### Processor Priorities

The processors in this library have the following priorities:

- `HibernateNewReadTransactionalRWProcessor`: 1,000,000
- `SerializableNewReadTransactionalRWProcessor`: 1,001,000

These high priority values ensure that these processors are executed after any custom processors you might have implemented with lower priorities.

## Configuration

You can control the activation of each processor using the following properties in your `application.properties`:

```properties
ru.code4a.transactional-rw.hibernate.enabled=true
ru.code4a.transactional-rw.serializable.enabled=true
```

Set the value to `true` to enable the respective processor, or `false` to disable it.

## Best Practices

1. Enable the Hibernate processor for applications using Hibernate ORM to optimize read-only transaction performance.
2. Use the Serializable processor when you need the highest level of isolation for your read-only transactions, but be aware of the potential performance impact.
3. Be mindful of the processor priorities when implementing custom processors to ensure proper execution order.

## Integration with Base Library

This extension library is designed to work seamlessly with the base Quarkus Transactional Read-Write Library. It enhances the `NewReadTransactionalRWProcessor` interface by providing specific implementations for Hibernate and serializable transactions.

To use these extensions effectively, make sure you have the base library properly set up in your project. Refer to the base library's README for information on using the `@TransactionalReadOnly` and `@TransactionalWrite` annotations.

## Limitations

- The Hibernate processor is specific to applications using Hibernate ORM with Quarkus.
- The Serializable processor may impact performance due to the higher isolation level and should be used judiciously.

# Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

# License

Apache 2.0
