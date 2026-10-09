# Migrating sdk-jpa 1.x to 2.0

2.0 only renames one package. Behaviour, mapped columns and properties (`codehunters.jpa.*`) are
unchanged; only imports change. In most IDEs, *Optimize/Organize Imports* after replacing the old
package below fixes every file.

| 1.x | 2.0 |
|---|---|
| `io.codehunters.jpa.domain.BaseEntity` | `io.codehunters.jpa.entity.BaseEntity` |
| `io.codehunters.jpa.domain.AuditableEntity` | `io.codehunters.jpa.entity.AuditableEntity` |

`auditing`, `config`, `converter`, `internal`, `pagination` and `specification` keep their names.
An application that lists entity packages explicitly (`@EntityScan`, `packagesToScan`) and named
`io.codehunters.jpa.domain` there must name `io.codehunters.jpa.entity` instead; mapped
superclasses are normally picked up through the entities that extend them.

## Logging

Logger categories follow the classes, so a level set on an old package or class name no longer
applies. Rename it the same way as the imports, for example:

```properties
# before
logging.level.io.codehunters.jpa.domain=DEBUG
# after
logging.level.io.codehunters.jpa.entity=DEBUG
```

A level on `io.codehunters` keeps working.
