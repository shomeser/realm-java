/*
 * Copyright 2014 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.realm;

import android.os.Handler;
import android.os.Looper;
import android.test.AndroidTestCase;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.realm.entities.Dog;

public class NotificationsTest extends AndroidTestCase {

    @Override
    protected void setUp() throws Exception {
        Realm.deleteRealmFile(getContext());
    }

    public void testFailingSetAutoRefreshOnNonLooperThread() throws ExecutionException, InterruptedException {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<Boolean> future = executorService.submit(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                Realm realm = Realm.getInstance(getContext());
                boolean autoRefresh = realm.isAutoRefresh();
                assertFalse(autoRefresh);
                try {
                    realm.setAutoRefresh(true);
                    return false;
                } catch (IllegalStateException ignored) {
                    return true;
                } finally {
                    realm.close();
                }
            }
        });
        assertTrue(future.get());
        assertTrue(Realm.realmsCache.get().isEmpty());
    }

    public void testSetAutoRefreshOnHandlerThread() throws ExecutionException, InterruptedException {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<Boolean> future = executorService.submit(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                Looper.prepare();
                Realm realm = Realm.getInstance(getContext());
                assertTrue(realm.isAutoRefresh());
                realm.setAutoRefresh(false);
                assertFalse(realm.isAutoRefresh());
                realm.setAutoRefresh(true);
                assertTrue(realm.isAutoRefresh());
                realm.close();
                return true;
            }
        });
        assertTrue(future.get());
        assertTrue(Realm.realmsCache.get().isEmpty());
    }

    public void testNotificationsNumber () throws InterruptedException, ExecutionException {
        final AtomicInteger counter = new AtomicInteger(0);
        final AtomicBoolean isReady = new AtomicBoolean(false);
        final Looper[] looper = new Looper[1];
        final AtomicBoolean isRealmOpen = new AtomicBoolean(true);

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<Boolean> future = executorService.submit(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                Realm realm = null;
                try {
                    Looper.prepare();
                    looper[0] = Looper.myLooper();
                    realm = Realm.getInstance(getContext());
                    realm.addChangeListener(new RealmChangeListener() {
                        @Override
                        public void onChange() {
                            counter.incrementAndGet();
                        }
                    });
                    isReady.set(true);
                    Looper.loop();
                } finally {
                    if (realm != null) {
                        realm.close();
                        isRealmOpen.set(false);
                    }
                }
                return true;
            }
        });

        // Wait until the looper is started
        while (!isReady.get()) {
            Thread.sleep(5);
        }
        Thread.sleep(100); 

        Realm realm = Realm.getInstance(getContext());
        realm.beginTransaction();
        Dog dog = realm.createObject(Dog.class);
        dog.setName("Rex");
        realm.commitTransaction();
        realm.close();

        try {
            future.get(1, TimeUnit.SECONDS);
        } catch (TimeoutException ignore) {
        } finally {
            looper[0].quit();
        }

        // Wait until the Looper thread is actually closed
        while (isRealmOpen.get()) {
            Thread.sleep(5);
        }

        assertEquals(1, counter.get());
        assertTrue(Realm.realmsCache.get().isEmpty());
    }

    public void testAutoUpdateRealmResults() throws InterruptedException, ExecutionException {
        final int TEST_SIZE = 10;
        final AtomicInteger counter = new AtomicInteger(0);
        final AtomicBoolean isReady = new AtomicBoolean(false);
        final AtomicBoolean isRealmOpen = new AtomicBoolean(true);
        final Map<Integer, Integer> results = new ConcurrentHashMap<Integer, Integer>();
        final Looper[] looper = new Looper[1];

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<Boolean> future = executorService.submit(new Callable<Boolean>() {

            @Override
            public Boolean call() throws Exception {
                Looper.prepare();
                looper[0] = Looper.myLooper();
                Realm realm = null;
                try {
                    realm = Realm.getInstance(getContext());
                    final RealmResults<Dog> dogs = realm.allObjects(Dog.class);
                    assertEquals(0, dogs.size());
                    realm.addChangeListener(new RealmChangeListener() {
                        @Override
                        public void onChange() {
                            int c = counter.incrementAndGet();
                            results.put(c, dogs.size());
                        }
                    });
                    isReady.set(true);
                    Looper.loop();
                } finally {
                    if (realm != null) {
                        realm.close();
                        isRealmOpen.set(false);
                    }
                }
                return true;
            }
        });

        // Wait until the looper is started
        while (!isReady.get()) {
            Thread.sleep(5);
        }
        Thread.sleep(100);

        Realm realm = Realm.getInstance(getContext());
        realm.beginTransaction();
        for (int i = 0; i < TEST_SIZE; i++) {
            Dog dog = realm.createObject(Dog.class);
            dog.setName("Rex " + i);
        }
        realm.commitTransaction();
        assertEquals(TEST_SIZE, realm.allObjects(Dog.class).size());
        realm.close();

        try {
            future.get(2, TimeUnit.SECONDS);
        } catch (TimeoutException ignore) {
        } finally {
            looper[0].quit();
        }

        // Wait until the Looper thread is actually closed
        while (isRealmOpen.get()) {
            Thread.sleep(5);
        }

        assertEquals(1, results.size());

        assertTrue(results.containsKey(1));
        assertEquals(TEST_SIZE, results.get(1).intValue());

        assertEquals(1, counter.get());
        assertTrue(Realm.realmsCache.get().isEmpty());
    }

    // TODO Disabled until we can figure out why this times out so often on the build server
    public void DISABLEDtestCloseClearingHandlerMessages() throws InterruptedException, TimeoutException, ExecutionException {
        final int TEST_SIZE = 10;
        final CountDownLatch backgroundLooperStarted = new CountDownLatch(1);
        final CountDownLatch addHandlerMessages = new CountDownLatch(1);

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<Boolean> future = executorService.submit(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                Looper.prepare(); // Fake background thread with a looper, eg. a IntentService
                Realm realm = Realm.getInstance(getContext());
                backgroundLooperStarted.countDown();

                // Random operation in the client code
                final RealmResults<Dog> dogs = realm.allObjects(Dog.class);
                if (dogs.size() != 0) {
                    return false;
                }
                addHandlerMessages.await(1, TimeUnit.SECONDS); // Wait for main thread to add update messages

                // Find the current Handler for the thread now. All message and references will be
                // cleared once we call close().
                Handler threadHandler = realm.getHandler();
                realm.close(); // Close native resources + associated handlers.

                // Looper now reads the update message from the main thread if the Handler was not
                // cleared. This will cause an IllegalStateException and should not happen.
                // If it works correctly. The looper will just block on an empty message queue.
                // This is normal behavior but is bad for testing, so we add a custom quit message
                // at the end so we can evaluate results faster.
                threadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Looper.myLooper().quit();
                    }
                });

                try {
                    Looper.loop();
                } catch (IllegalStateException e) {
                    return false;
                }
                return true;
            }
        });

        // Wait until the looper is started on a background thread
        backgroundLooperStarted.await(1, TimeUnit.SECONDS);

        // Execute a transaction that will trigger a Realm update
        Realm realm = Realm.getInstance(getContext());
        realm.beginTransaction();
        for (int i = 0; i < TEST_SIZE; i++) {
            Dog dog = realm.createObject(Dog.class);
            dog.setName("Rex " + i);
        }
        realm.commitTransaction();
        assertEquals(TEST_SIZE, realm.allObjects(Dog.class).size());
        realm.close();
        addHandlerMessages.countDown();

        // Check that messages was properly cleared
        // It looks like getting this future sometimes takes a while for some reason. Setting to
        // 10s. now.
        Boolean result = future.get(10, TimeUnit.SECONDS);
        assertTrue(result);
    }

    public void testHandlerNotRemovedToSoon() {
        Realm.deleteRealmFile(getContext(), "private-realm");
        Realm instance1 = Realm.getInstance(getContext(), "private-realm");
        Realm instance2 = Realm.getInstance(getContext(), "private-realm");
        assertEquals(instance1.getId(), instance2.getId());
        assertNotNull(instance1.getHandler());

        // If multiple instances are open on the same thread, don't remove handler on that thread
        // until last instance is closed.
        instance2.close();
        assertNotNull(instance1.getHandler());
        instance1.close();
        assertNull(instance1.getHandler());
    }
}
