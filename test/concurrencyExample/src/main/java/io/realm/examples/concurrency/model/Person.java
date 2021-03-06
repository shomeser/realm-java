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

package io.realm.examples.service.model;

import io.realm.RealmList;
import io.realm.RealmObject;

public class Person extends RealmObject {

    private String name;
    private int age;
//    private Dog dog;
//    private RealmList<Cat> cats;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

//    public Dog getDog() {
//        return dog;
//    }
//
//    public void setDog(Dog dog) {
//        this.dog = dog;
//    }
//
//    public RealmList<Cat> getCats() {
//        return cats;
//    }
//
//    public void setCats(RealmList<Cat> cats) {
//        this.cats = cats;
//    }

//    @Override
//    public String toString() {
//        return "Person{" +
//                "name='" + name + '\'' +
//                ", age=" + age +
//                ", dog=" + dog +
//                ", cats=" + cats +
//                '}';
//    }
}