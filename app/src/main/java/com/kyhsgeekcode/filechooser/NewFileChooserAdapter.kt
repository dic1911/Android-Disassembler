package com.kyhsgeekcode.filechooser

import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.kyhsgeekcode.disassembler.R
import com.kyhsgeekcode.filechooser.model.FileItem
import kotlinx.android.synthetic.main.new_file_chooser_row.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.collections.ArrayList

class NewFileChooserAdapter(
        private
        val parentActivity: NewFileChooserActivity
) : RecyclerView.Adapter<NewFileChooserAdapter.ViewHolder>() {
    val TAG = "Adapter"
    private val values: MutableList<FileItem> = ArrayList()
    val onClickListener: View.OnClickListener
    val backStack = Stack<FileItem>()
    var currentParentItem: FileItem = FileItem.rootItem

    init {
        backStack.clear()
        // backStack.push(FileItem.rootItem)
        currentParentItem = FileItem.rootItem
        onClickListener = View.OnClickListener { v ->
            val item = v.tag as FileItem
            if (!item.isAccessible()) {
                Toast.makeText(parentActivity, "the file is inaccessible", Toast.LENGTH_SHORT).show()
                return@OnClickListener
            }
            if (item.canExpand()) {
                // 물어본다.
                if (!item.isProjectAble() && !item.isRawAvailable()) {
                    navigateInto(item)
                    return@OnClickListener
                }
                AlertDialog.Builder(parentActivity)
                        .setTitle("Choose Action")
                        .also {
                            if (item.isProjectAble()) {
                                it.setPositiveButton("Open as project") { _: DialogInterface, _: Int ->
                                    parentActivity.openAsProject(item)
                                }
                            }
                        }.also {
                            if (item.isRawAvailable()) {
                                it.setNeutralButton("Open raw") { _, _ ->
                                    parentActivity.openRaw(item)
                                }
                            }
                        }
                        .setNegativeButton("Navigate into") { _, _ ->
                            navigateInto(item)
                        }.show()
            } else {
                // 물어보고 진행한다.
                AlertDialog.Builder(parentActivity)
                        .setTitle("Open the file ${item.text}?")
                        .setPositiveButton("Open") { _, _ ->
                            parentActivity.openRaw(item)
                        }.setNegativeButton("No") { dialog, _ ->
                            dialog.cancel()
                        }.show()
            }
        }
        values.addAll(FileItem.rootItem.listSubItems())
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName = view.textViewNewItemName
        val ivIcon = view.imageViewFileIcon
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // Log.d(TAG,"onCreateViewHolder")
        val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.new_file_chooser_row, parent, false)
//        listView = parent as RecyclerView
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = values.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = values[position]
        with(holder.itemView) {
            tag = item
            setOnClickListener(onClickListener)
        }
        with(holder.tvName) {
            text = item.text
        }
        holder.ivIcon.setImageDrawable(item.drawable)
    }

    private fun navigateInto(item: FileItem) {
        CoroutineScope(Dispatchers.Default).launch {
            val subItems = listSubItems(item)
            if (subItems.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(parentActivity, "The item has no children", Toast.LENGTH_SHORT).show()
                }
            }
            addItemsToListSorted(subItems)
            backStack.push(currentParentItem)
            currentParentItem = item
            withContext(Dispatchers.Main) {
                notifyDataSetChanged()
            }
        }
    }

    private fun addItemsToListSorted(subItems: List<FileItem>) {
        values.clear()
        values.addAll(subItems)
        values.sortWith(compareBy({ !it.text.endsWith("/") }, { it.text }))
    }

    fun onBackPressedShouldFinish(): Boolean {
        if (backStack.empty()) return true
        val lastItem = backStack.pop()
        currentParentItem = lastItem
        CoroutineScope(Dispatchers.Default).launch {
            val items = listSubItems(currentParentItem)
            addItemsToListSorted(items)
            withContext(Dispatchers.Main) {
                notifyDataSetChanged()
            }
        }
        return false
    }

    private suspend fun listSubItems(item: FileItem): List<FileItem> {
        var showedTotal = false
        return withContext(Dispatchers.IO) {
            val ret = item.listSubItems { current, total ->
                CoroutineScope(Dispatchers.Main).launch {
                    parentActivity.publishProgress(current, if (showedTotal) null else total)
                    showedTotal = true
                }
            }
            CoroutineScope(Dispatchers.Main).launch {
                parentActivity.finishProgress()
            }
            ret
        }
    }
}
