package me.hajoo.cryptopediaserver.service

import me.hajoo.cryptopediaserver.entity.Cryptocurrency
import me.hajoo.cryptopediaserver.repository.CryptocurrencyRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class CryptocurrencyService(
    private val cryptocurrencyRepository: CryptocurrencyRepository
) {
    
    fun getAllCryptocurrencies(): List<Cryptocurrency> {
        return cryptocurrencyRepository.findAll()
    }
    
    @Transactional
    fun saveCryptocurrency(cryptocurrency: Cryptocurrency): Cryptocurrency {
        return cryptocurrencyRepository.save(cryptocurrency)
    }
}
