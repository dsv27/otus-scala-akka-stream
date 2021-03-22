package homework

import java.time.ZonedDateTime
import io.circe.syntax._
import io.circe.generic.JsonCodec
import io.circe.generic.auto._
import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder, HCursor, Json}
import io.circe.Decoder.Result
import stellar.sdk.model.op.{Operation, Transacted}
import stellar.sdk.model.response.{EffectResponse, LedgerResponse}
import stellar.sdk.model.result.TransactionHistory
import stellar.sdk.model.{NativeAmount, Memo, MemoText, ClaimableBalanceId}
import stellar.sdk.{PublicKey, PublicKeyOps}
import stellar.sdk.model.ledger.ClaimableBalanceKey


object Model {
  @JsonCodec
  case class LedgerWrapper(_id: String, ledgerResponse: LedgerResponse)

  object LedgerWrapper {
    def apply(ledgerResponse: LedgerResponse): LedgerWrapper =
      new LedgerWrapper(ledgerResponse.id, ledgerResponse)
  }
implicit val LedgerWrapperEncoder: Encoder[LedgerWrapper] =
    deriveEncoder[LedgerWrapper]
    
  case class TransactionWrapper(
      id: String,
      transactionHistory: TransactionHistory
  )

  implicit val transactionWrapperEncoder: Encoder[TransactionWrapper] =
    deriveEncoder[TransactionWrapper]
  implicit val TransactionHistoryEncoder: Encoder[TransactionHistory] =
    deriveEncoder[TransactionHistory]
  implicit val MemoEncoder: Encoder[Memo] = { memo =>
    memo.toString().asJson
  }

  implicit val MemoTextEncoder: Encoder[MemoText] = { memoText =>
    memoText.toString().asJson
  }
  implicit val PublicKeyEncoder: Encoder[PublicKey] = { pbKey =>
    pbKey.toString().asJson
  }

  object TransactionWrapper {
    def apply(transactionHistory: TransactionHistory): TransactionWrapper =
      new TransactionWrapper(
        s"${transactionHistory.ledgerId}_${transactionHistory.sequence}",
        transactionHistory
      )
  }

  case class OperationWrapper(_id: String, operation: Transacted[Operation])

  implicit val OperationWrapperEncoder: Encoder[OperationWrapper] =
    deriveEncoder[OperationWrapper]

  implicit val OperationEncoder: Encoder[Operation] = deriveEncoder[Operation]

  implicit val PublicKeyOpsEncoder: Encoder[PublicKeyOps] = { publicKeyOps =>
    publicKeyOps.toString().asJson
  }

  implicit val ClaimableBalanceKeyEncoder: Encoder[ClaimableBalanceKey] = {
    claimableBalanceKey => claimableBalanceKey.toString().asJson
  }
  implicit val ClaimableBalanceIdEncoder: Encoder[ClaimableBalanceId] = {
    claimableBalanceId => claimableBalanceId.toString().asJson
  }

  object OperationWrapper {
    def apply(operation: Transacted[Operation]): OperationWrapper =
      new OperationWrapper(s"${operation.txnHash}_${operation.id}", operation)
  }

  case class EffectWrapper(_id: String, effectResponse: EffectResponse)
  implicit val EffectWrapperEncoder: Encoder[EffectWrapper] =
    deriveEncoder[EffectWrapper]

  object EffectWrapper {
    def apply(effectResponse: EffectResponse): EffectWrapper =
      new EffectWrapper(effectResponse.id, effectResponse: EffectResponse)
  }

}
