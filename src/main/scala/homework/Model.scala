package homework

import java.time.ZonedDateTime

import io.circe.generic.JsonCodec
import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder, HCursor, Json}
import io.circe.Decoder.Result
import stellar.sdk.model.op.{Operation, Transacted}
import stellar.sdk.model.response.{EffectResponse, LedgerResponse}
import stellar.sdk.model.result.TransactionHistory
import stellar.sdk.model.NativeAmount

object Model {

  case
  class LedgerWrapper(_id: String, ledgerResponse: LedgerResponse)

  object LedgerWrapper {
    def apply(ledgerResponse: LedgerResponse): LedgerWrapper = new LedgerWrapper(ledgerResponse.id, ledgerResponse)
  }

  //  @JsonCodec
  case
  class TransactionWrapper(_id: String, transactionHistory: TransactionHistory)

  object TransactionWrapper {
    def apply(transactionHistory: TransactionHistory): TransactionWrapper = new TransactionWrapper(s"${transactionHistory.ledgerId}_${transactionHistory.sequence}", transactionHistory)
  }

  //  @JsonCodec
  case
  class OperationWrapper(_id: String, operation: Transacted[Operation])

  object OperationWrapper {
    def apply(operation: Transacted[Operation]): OperationWrapper = new OperationWrapper(s"${operation.txnHash}_${operation.id}", operation)
  }

  //  @JsonCodec
  case
  class EffectWrapper(_id: String, effectResponse: EffectResponse)

  object EffectWrapper {
    def apply(effectResponse: EffectResponse): EffectWrapper = new EffectWrapper(effectResponse.id, effectResponse: EffectResponse)
  }

}
