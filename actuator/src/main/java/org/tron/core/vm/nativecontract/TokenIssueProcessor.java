package org.tron.core.vm.nativecontract;

import org.tron.common.utils.ByteArray;
import org.tron.common.utils.DecodeUtil;
import org.tron.core.capsule.AccountCapsule;
import org.tron.core.capsule.AssetIssueCapsule;
import org.tron.core.exception.ContractExeException;
import org.tron.core.exception.ContractValidateException;
import org.tron.core.utils.TransactionUtil;
import org.tron.core.vm.nativecontract.param.TokenIssueParam;
import org.tron.core.vm.repository.Repository;

import java.util.Objects;

import static org.tron.core.vm.nativecontract.ContractProcessorConstant.*;

public class TokenIssueProcessor {

    public void execute(Object contract, Repository repository) throws ContractExeException {
        TokenIssueParam tokenIssueParam = (TokenIssueParam) contract;
        long tokenIdNum = repository.getTokenIdNum();
        tokenIdNum++;
        repository.saveTokenIdNum(tokenIdNum);
        AssetIssueCapsule assetIssueCapsule = new AssetIssueCapsule(tokenIssueParam.getOwnerAddress(),
                Long.toString(tokenIdNum), ByteArray.toStr(tokenIssueParam.getName()),
                ByteArray.toStr(tokenIssueParam.getAbbr()), tokenIssueParam.getTotalSupply(),
                tokenIssueParam.getPrecision());
        repository.putAssetIssueValue(assetIssueCapsule.createDbV2Key(), assetIssueCapsule);
        AccountCapsule accountCapsule = repository.getAccount(tokenIssueParam.getOwnerAddress());
        accountCapsule.setAssetIssuedName(assetIssueCapsule.getName().toByteArray());
        accountCapsule.setAssetIssuedID(ByteArray.fromString(assetIssueCapsule.getId()));
        accountCapsule
                .addAssetV2(assetIssueCapsule.createDbV2Key(), tokenIssueParam.getTotalSupply());
        accountCapsule.setInstance(accountCapsule.getInstance().toBuilder().build());
        // spend 1024trx for assetissue, send to blackhole address
        AccountCapsule bhAccountCapsule = repository.getAccount(repository.getBlackHoleAddress());
        bhAccountCapsule.setBalance(Math.addExact(bhAccountCapsule.getBalance(), TOKEN_ISSUE_FEE));
        accountCapsule.setBalance(Math.subtractExact(accountCapsule.getBalance(), TOKEN_ISSUE_FEE));
        repository.putAccountValue(tokenIssueParam.getOwnerAddress(), accountCapsule);
        repository.putAccountValue(bhAccountCapsule.getAddress().toByteArray(), bhAccountCapsule);
    }

    public void validate(Object contract, Repository repository) throws ContractValidateException {
        if (Objects.isNull(contract)) {
            throw new ContractValidateException(CONTRACT_NULL);
        }
        if (!(contract instanceof TokenIssueParam)) {
            throw new ContractValidateException(
                    "contract type error,expected type [TokenIssuedContract],real type[" + contract
                            .getClass() + "]");
        }
        TokenIssueParam tokenIssueParam = (TokenIssueParam) contract;
        if (repository == null) {
            throw new ContractValidateException(STORE_NOT_EXIST);
        }
        if (!DecodeUtil.addressValid(tokenIssueParam.getOwnerAddress())) {
            throw new ContractValidateException("Invalid ownerAddress");
        }
        if (!TransactionUtil.validAssetName(tokenIssueParam.getName())) {
            throw new ContractValidateException("Invalid assetName");
        }
        if ((TRX.equals(tokenIssueParam.getName().toString().toLowerCase()))) {
            throw new ContractValidateException("assetName can't be trx");
        }
        if (tokenIssueParam.getPrecision() < 0 || tokenIssueParam.getPrecision() > 6) {
            throw new ContractValidateException("precision cannot exceed 6");
        }
        if (Objects.nonNull(tokenIssueParam.getAbbr()) && !TransactionUtil.validAssetName(tokenIssueParam.getAbbr())) {
            throw new ContractValidateException("Invalid abbreviation for token");
        }
        if (tokenIssueParam.getTotalSupply() <= 0) {
            throw new ContractValidateException("TotalSupply must greater than 0!");
        }
        AccountCapsule accountCapsule = repository.getAccount(tokenIssueParam.getOwnerAddress());
        if (accountCapsule == null) {
            throw new ContractValidateException("Account not exists");
        }
        if (!accountCapsule.getAssetIssuedName().isEmpty()) {
            throw new ContractValidateException("An account can only issue one asset");
        }
        if (accountCapsule.getBalance() < repository.getDynamicPropertiesStore().getAssetIssueFee()) {
            throw new ContractValidateException("No enough balance for fee!");
        }
    }
}
