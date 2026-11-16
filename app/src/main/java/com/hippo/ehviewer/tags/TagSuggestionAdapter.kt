package com.hippo.ehviewer.tags

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView
import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.EhTagDatabase
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

class TagSuggestionAdapter(private val context: Context) : BaseAdapter(), Filterable {
    private data class Suggestion(val replacement: String, val label: String, val translation: String?)
    private var suggestions = emptyList<Suggestion>()
    override fun getCount(): Int = suggestions.size
    override fun getItem(position: Int): Any = suggestions[position]
    override fun getItemId(position: Int): Long = position.toLong()
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_2, parent, false)
        val item = suggestions[position]
        view.findViewById<TextView>(android.R.id.text1).text = item.label
        view.findViewById<TextView>(android.R.id.text2).apply {
            text = item.translation.orEmpty()
            visibility = if (item.translation.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        return view
    }

    private val suggestionFilter = object : Filter() {
        // Android Filter runs this on its worker thread, never on the UI thread.
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val token = constraint?.toString().orEmpty()
            val operator = token.takeWhile { it == '-' || it == '~' }
            val keyword = token.removePrefix(operator).replace("\"", "").removeSuffix("$").replace('_', ' ')
            val results = mutableListOf<Suggestion>()
            if (keyword.isNotBlank() && context.applicationContext is EhApplication) {
                runBlocking {
                    val prefix = keyword.substringBefore(':', "")
                    val dictionaryKeyword = when {
                        prefix == "tag" -> keyword.substringAfter(':')
                        prefix.isNotEmpty() -> TagQuery.canonicalNamespace(prefix) + ":" + keyword.substringAfter(':')
                        else -> keyword
                    }
                    if (EhTagDatabase.isInitialized()) {
                        for (type in listOf(EhTagDatabase.TYPE_EQUAL, EhTagDatabase.TYPE_START, EhTagDatabase.TYPE_CONTAIN)) {
                            EhTagDatabase.suggestFlow(dictionaryKeyword, Settings.showTagTranslations && EhTagDatabase.isTranslatable(context), type)
                                .take(20).toList().forEach { (hint, raw) ->
                                    results.add(Suggestion(TagCompletion.replacement(raw, operator), raw, hint))
                                }
                        }
                    }
                    val namespace = prefix.takeIf { it.isNotEmpty() && it != "tag" }?.let(TagQuery::canonicalNamespace)
                    GalleryTagDatabase.get(context).tags().suggest(namespace, "%${keyword.substringAfter(':')}%")
                        .forEach { tag ->
                            val raw = if (tag.namespace.isEmpty()) tag.name else "${tag.namespace}:${tag.name}"
                            results.add(Suggestion(TagCompletion.replacement(raw, operator), raw, null))
                        }
                }
            }
            val unique = results.distinctBy { TagQuery.canonicalNamespace(it.label.substringBefore(':', "")) + ":" + it.label.substringAfter(':') }.take(30)
            return FilterResults().apply {
                values = unique
                count = unique.size
            }
        }

        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            @Suppress("UNCHECKED_CAST")
            val items = results?.values as? List<Suggestion>
            suggestions = items.orEmpty()
            notifyDataSetChanged()
        }

        override fun convertResultToString(resultValue: Any?): CharSequence = (resultValue as? Suggestion)?.replacement.orEmpty()
    }
    override fun getFilter(): Filter = suggestionFilter
}
