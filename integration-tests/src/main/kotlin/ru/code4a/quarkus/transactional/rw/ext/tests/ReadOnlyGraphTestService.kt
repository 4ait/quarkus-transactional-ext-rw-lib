package ru.code4a.quarkus.transactional.rw.ext.tests

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.Entity
import jakarta.persistence.EntityManager
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import org.hibernate.Hibernate
import org.hibernate.Session
import org.hibernate.jpa.SpecHints
import ru.code4a.quarkus.transactional.rw.annotations.TransactionalReadOnly
import ru.code4a.quarkus.transactional.rw.annotations.TransactionalWrite

// The model of HHH-20251: Quarkus enhances these entities for lazy loading.
@Entity
open class GraphAccount {
    @Id
    open var id: Long = 0

    @OneToOne(fetch = FetchType.LAZY)
    open var userName: GraphUserName? = null

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "account")
    open var assignments: MutableSet<GraphAssignment> = mutableSetOf()
}

@Entity
open class GraphUserName {
    @Id
    open var id: Long = 0

    @OneToOne(mappedBy = "userName", fetch = FetchType.LAZY)
    open var account: GraphAccount? = null

    @OneToOne(mappedBy = "userName", fetch = FetchType.LAZY)
    open var assignment: GraphAssignment? = null
}

@Entity
open class GraphAssignment {
    @Id
    open var id: Long = 0

    @OneToOne
    open var userName: GraphUserName? = null

    @ManyToOne
    open var account: GraphAccount? = null
}

data class ReadOnlyGraphResult(
    val defaultReadOnly: Boolean,
    val accountReadOnly: Boolean,
    val userNameId: Long?,
    val assignmentUserNameIds: Set<Long>,
    val graphInitialized: Boolean
)

@ApplicationScoped
open class ReadOnlyGraphTestService {
    @Inject
    lateinit var entityManager: EntityManager

    @TransactionalWrite
    open fun createGraph() {
        if (entityManager.find(GraphAccount::class.java, ACCOUNT_ID) != null) return
        val first = GraphUserName().apply { id = 1 }
        val second = GraphUserName().apply { id = 2 }
        entityManager.persist(first)
        entityManager.persist(second)
        val account = GraphAccount().apply { id = ACCOUNT_ID; userName = first }
        entityManager.persist(account)
        // The second assignment refers to the account's own user name: the query meets
        // that already initialized read-only instance again while reading the same rows.
        entityManager.persist(GraphAssignment().apply { id = 1; this.account = account; userName = second })
        entityManager.persist(GraphAssignment().apply { id = 2; this.account = account; userName = first })
    }

    @TransactionalReadOnly
    open fun loadAccountWithFetchGraph(): ReadOnlyGraphResult {
        val session = entityManager.unwrap(Session::class.java)
        val graph = entityManager.createEntityGraph(GraphAccount::class.java)
        graph.addSubgraph<GraphUserName>("userName")
        graph.addSubgraph<GraphAssignment>("assignments").addSubgraph<GraphUserName>("userName")
        val account = entityManager
            .createQuery("select a from GraphAccount a where a.id = :id", GraphAccount::class.java)
            .setParameter("id", ACCOUNT_ID)
            .setHint(SpecHints.HINT_SPEC_FETCH_GRAPH, graph)
            .singleResult
        return ReadOnlyGraphResult(
            session.isDefaultReadOnly,
            session.isReadOnly(account),
            account.userName?.id,
            account.assignments.mapNotNull { it.userName?.id }.toSet(),
            Hibernate.isInitialized(account.userName) && Hibernate.isInitialized(account.assignments) &&
                account.assignments.all { Hibernate.isInitialized(it.userName) }
        )
    }

    private companion object {
        const val ACCOUNT_ID = 1L
    }
}
