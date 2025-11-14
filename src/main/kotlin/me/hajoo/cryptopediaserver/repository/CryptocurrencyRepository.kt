package me.hajoo.cryptopediaserver.repository

import me.hajoo.cryptopediaserver.entity.Cryptocurrency
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface CryptocurrencyRepository : JpaRepository<Cryptocurrency, Long>
