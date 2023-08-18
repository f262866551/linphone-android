/*
 * Copyright (c) 2010-2023 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.ui.main.contacts.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.navigation.navGraphViewModels
import kotlinx.coroutines.launch
import org.linphone.BR
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.ContactNewOrEditFragmentBinding
import org.linphone.ui.main.MainActivity
import org.linphone.ui.main.contacts.model.NewOrEditNumberOrAddressModel
import org.linphone.ui.main.contacts.viewmodel.ContactNewOrEditViewModel
import org.linphone.ui.main.fragment.GenericFragment
import org.linphone.utils.FileUtils

class EditContactFragment : GenericFragment() {
    companion object {
        const val TAG = "[Edit Contact Fragment]"
    }

    private lateinit var binding: ContactNewOrEditFragmentBinding

    private val viewModel: ContactNewOrEditViewModel by navGraphViewModels(
        R.id.editContactFragment
    )

    private val args: EditContactFragmentArgs by navArgs()

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            Log.i("$TAG Picture picked [$uri]")
            // TODO FIXME: use a better file name
            val localFileName = FileUtils.getFileStoragePath("temp", true)
            lifecycleScope.launch {
                if (FileUtils.copyFile(uri, localFileName)) {
                    viewModel.picturePath.postValue(localFileName.absolutePath)
                } else {
                    Log.e(
                        "$TAG Failed to copy file from [$uri] to [${localFileName.absolutePath}]"
                    )
                }
            }
        } else {
            Log.w("$TAG No picture picked")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ContactNewOrEditFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun goBack() {
        findNavController().popBackStack()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel

        postponeEnterTransition()

        val refKey = args.contactRefKey
        Log.i("[Contact Edit Fragment] Looking up for contact with ref key [$refKey]")
        viewModel.findFriendByRefKey(refKey)

        binding.setCancelClickListener {
            goBack()
        }

        binding.setPickImageClickListener {
            pickImage()
        }

        viewModel.saveChangesEvent.observe(viewLifecycleOwner) {
            it.consume { refKey ->
                if (refKey.isNotEmpty()) {
                    Log.i("$TAG Changes were applied, going back to details page")
                    goBack()
                } else {
                    // TODO : show error
                }
            }
        }

        viewModel.friendFoundEvent.observe(viewLifecycleOwner) {
            it.consume { found ->
                if (found) {
                    binding.sipAddresses.removeAllViews()
                    for (items in viewModel.sipAddresses) {
                        addCell(items)
                    }
                    binding.phoneNumbers.removeAllViews()
                    for (items in viewModel.phoneNumbers) {
                        addCell(items)
                    }
                    startPostponedEnterTransition()
                }
            }
        }

        viewModel.addNewNumberOrAddressFieldEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                addCell(model)
            }
        }

        viewModel.removeNewNumberOrAddressFieldEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                removeCell(model)
            }
        }
    }

    private fun addCell(model: NewOrEditNumberOrAddressModel) {
        val inflater = requireContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val parent = if (model.isSip) binding.sipAddresses else binding.phoneNumbers

        val cellBinding = DataBindingUtil.inflate<ViewDataBinding>(
            inflater,
            R.layout.contact_new_or_edit_cell,
            parent,
            false
        )
        cellBinding.setVariable(BR.model, model)
        cellBinding.lifecycleOwner = (requireActivity() as MainActivity)

        parent.addView(cellBinding.root)
    }

    private fun removeCell(model: NewOrEditNumberOrAddressModel) {
        val parent = if (model.isSip) binding.sipAddresses else binding.phoneNumbers
        parent.removeAllViews()
        val source = if (model.isSip) viewModel.sipAddresses else viewModel.phoneNumbers
        for (items in source) {
            addCell(items)
        }
    }

    private fun pickImage() {
        pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
}
