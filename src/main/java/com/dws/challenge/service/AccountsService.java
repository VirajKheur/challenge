package com.dws.challenge.service;

import com.dws.challenge.domain.Account;
import com.dws.challenge.repository.AccountsRepository;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
public class AccountsService {

  @Getter
  private final AccountsRepository accountsRepository;

  @Autowired
  EmailNotificationService emailNotificationService;

  // lock for thread safety to prevent race condition to update balance
  private final ReentrantLock lock = new ReentrantLock();

  @Autowired
  public AccountsService(AccountsRepository accountsRepository) {
    this.accountsRepository = accountsRepository;
  }

  public void createAccount(Account account) {
    this.accountsRepository.createAccount(account);
  }

  public Account getAccount(String accountId) {
    return this.accountsRepository.getAccount(accountId);
  }

  public void deposit(Account account,BigDecimal depositAmount){
    lock.lock();
    try{
      BigDecimal balance= account.getBalance();
      balance = balance.add(depositAmount);
      account.setBalance(balance);
      // we can add update account in repository so that it will reflect in db
      //this.accountsRepository.update(account);
    }finally {
      lock.unlock();
    }

  }

  public boolean withdraw(Account account,BigDecimal withdrawAmount){
    lock.lock();
    try{
      BigDecimal balance= account.getBalance();
      if(balance.compareTo(withdrawAmount) < 0){
        return false;
      }
      else{
        balance = balance.subtract(withdrawAmount);
        account.setBalance(balance);
        // we can add update account in repository so that it will reflect in db
        //this.accountsRepository.update(account);
        return true;
      }
    }finally {
      lock.unlock();
    }

  }

  @Transactional
  public boolean transfer(String fromAccountId,String toAccountId,BigDecimal balance){
    Account fromAccount = getAccount(fromAccountId);
    Account toAccount = getAccount(toAccountId);

    if(Objects.isNull(fromAccount) || Objects.isNull(toAccount)){
      log.error("Invalid account ID's. ");
    }
    // Avoiding deadlock by fixing order of lock
    Account firstAccountLock = fromAccount.getAccountId().compareTo(toAccount.getAccountId()) < 0 ? fromAccount : toAccount;
    Account secondAccountLock = fromAccountId.compareTo(toAccountId) < 0 ? toAccount : fromAccount;

    synchronized (firstAccountLock) {
      synchronized (secondAccountLock) {
        if (!withdraw(firstAccountLock,balance)) {
         log.error("Insufficient balance.");
          return false;
        }
        deposit(secondAccountLock,balance);
      }
    }
    //log.info("Transfer successful: " + balance + " from " + fromAccountId + " to " + toAccountId);
    //notifying users about credit and deposit
    emailNotificationService.notifyAboutTransfer(fromAccount,balance + " debited: from " + fromAccountId + " to " + toAccountId);
    emailNotificationService.notifyAboutTransfer(toAccount,balance + " credited: to " + toAccountId + " from "+ fromAccountId);
    return true;
  }

}
