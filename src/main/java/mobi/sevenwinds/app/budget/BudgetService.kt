package mobi.sevenwinds.app.budget

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mobi.sevenwinds.app.author.AuthorEntity
import mobi.sevenwinds.app.author.AuthorTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

object BudgetService {
    suspend fun addRecord(body: BudgetRecord): BudgetRecord = withContext(Dispatchers.IO) {
        transaction {
            val entity = BudgetEntity.new {
                this.year = body.year
                this.month = body.month
                this.amount = body.amount
                this.type = body.type
                this.author = body.authorId?.let { id ->
                    AuthorEntity.findById(id) ?: error("јвтор с ID $id не найден")
                }
            }

            return@transaction entity.toResponse()
        }
    }

    suspend fun getYearStats(param: BudgetYearParam): BudgetYearStatsResponse = withContext(Dispatchers.IO) {
        transaction {
            val query = BudgetTable
                .leftJoin(AuthorTable)
                .select { BudgetTable.year eq param.year }
                .apply {
                    param.authorName?.let { authorName ->
                        andWhere { AuthorTable.name.lowerCase().like("%${authorName.toLowerCase()}%") }
                    }
                }
                .orderBy(BudgetTable.month to SortOrder.ASC, BudgetTable.amount to SortOrder.DESC)
                .limit(param.limit, param.offset)

            val total = BudgetTable
                .leftJoin(AuthorTable)
                .select { BudgetTable.year eq param.year}
                .apply { param.authorName?.let { authorName ->
                    andWhere { AuthorTable.name.lowerCase().like("%${authorName.toLowerCase()}%") }
                }
                }.count()

            val sumByType = BudgetTable
                .leftJoin(AuthorTable)
                .slice(BudgetTable.type, BudgetTable.amount.sum())
                .select { BudgetTable.year eq param.year }
                .apply {
                    param.authorName?.let { authorName ->
                        andWhere { AuthorTable.name.lowerCase().like("%${authorName.toLowerCase()}%") }
                    }
                }
                .groupBy(BudgetTable.type)
                .associate { it[BudgetTable.type].name to it[BudgetTable.amount.sum()]!! }

            val data = BudgetEntity.wrapRows(query).map { it.toResponse() }


            return@transaction BudgetYearStatsResponse(
                total = total,
                totalByType = sumByType,
                items = data
            )
        }
    }
}