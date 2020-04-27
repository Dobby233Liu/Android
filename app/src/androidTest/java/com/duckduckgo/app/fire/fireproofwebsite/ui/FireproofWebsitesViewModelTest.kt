/*
 * Copyright (c) 2020 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.app.fire.fireproofwebsite.ui

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.InstantSchedulersRule
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteDao
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteEntity
import com.duckduckgo.app.fire.fireproofwebsite.ui.FireproofWebsitesViewModel.Command.ConfirmDeleteFireproofWebsite
import com.duckduckgo.app.global.db.AppDatabase
import com.nhaarman.mockitokotlin2.atLeastOnce
import com.nhaarman.mockitokotlin2.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.verify

class FireproofWebsitesViewModelTest {

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val schedulers = InstantSchedulersRule()

    @ExperimentalCoroutinesApi
    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private lateinit var fireproofWebsiteDao: FireproofWebsiteDao

    private lateinit var viewModel: FireproofWebsitesViewModel

    private lateinit var db: AppDatabase

    private val commandCaptor = ArgumentCaptor.forClass(FireproofWebsitesViewModel.Command::class.java)

    private val viewStateCaptor = ArgumentCaptor.forClass(FireproofWebsitesViewModel.ViewState::class.java)

    private val mockCommandObserver: Observer<FireproofWebsitesViewModel.Command> = mock()

    private val mockViewStateObserver: Observer<FireproofWebsitesViewModel.ViewState> = mock()

    @Before
    fun before() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        fireproofWebsiteDao = db.fireproofWebsiteDao()
        viewModel = FireproofWebsitesViewModel(fireproofWebsiteDao, coroutineRule.testDispatcherProvider)
        viewModel.command.observeForever(mockCommandObserver)
        viewModel.viewState.observeForever(mockViewStateObserver)
    }

    @After
    fun after() {
        db.close()
        viewModel.command.removeObserver(mockCommandObserver)
        viewModel.viewState.removeObserver(mockViewStateObserver)
    }

    @Test
    fun whenUserDeletesFireProofWebsiteThenConfirmDeleteCommandIssued() {
        val fireproofWebsiteEntity = FireproofWebsiteEntity("domain.com")
        viewModel.onDeleteRequested(fireproofWebsiteEntity)

        assertCommandIssued<ConfirmDeleteFireproofWebsite> {
            assertEquals(fireproofWebsiteEntity, this.entity)
        }
    }

    @Test
    fun whenUserConfirmsToDeleteThenEntityRemovedAndViewStateUpdated() {
        givenFireproofWebsiteDomain("domain.com")

        viewModel.delete(FireproofWebsiteEntity("domain.com"))

        verify(mockViewStateObserver, atLeastOnce()).onChanged(viewStateCaptor.capture())
        assertTrue(viewStateCaptor.value.fireproofWebsitesEntities.isEmpty())
    }

    @Test
    fun whenViewModelInitialisedThenViewStateShowsCurrentFireproofWebsites() {
        givenFireproofWebsiteDomain("domain.com")

        verify(mockViewStateObserver, atLeastOnce()).onChanged(viewStateCaptor.capture())
        assertTrue(viewStateCaptor.value.fireproofWebsitesEntities.size == 1)
    }

    private inline fun <reified T : FireproofWebsitesViewModel.Command> assertCommandIssued(instanceAssertions: T.() -> Unit = {}) {
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        val issuedCommand = commandCaptor.allValues.find { it is T }
        assertNotNull(issuedCommand)
        (issuedCommand as T).apply { instanceAssertions() }
    }

    private fun givenFireproofWebsiteDomain(vararg fireproofWebsitesDomain: String) {
        fireproofWebsitesDomain.forEach {
            fireproofWebsiteDao.insert(FireproofWebsiteEntity(domain = it))
        }
    }
}
