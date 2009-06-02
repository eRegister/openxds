/**
 *
 *  Copyright (C) 2009 SYSNET International <support@sysnetint.com>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 *
 */
package org.openhealthexchange.openxds.service.impl;

import org.openhealthexchange.openxds.Constants;
import org.openhealthexchange.openxds.dao.RoleDao;
import org.openhealthexchange.openxds.dao.UserDao;
import org.openhealthexchange.openxds.model.Role;
import org.openhealthexchange.openxds.model.User;
import org.openhealthexchange.openxds.service.UserExistsException;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.dao.DataIntegrityViolationException;

public class UserManagerImplTest extends BaseManagerMockTestCase {
    //~ Instance fields ========================================================
    private UserManagerImpl userManager = new UserManagerImpl();
    private RoleManagerImpl roleManager = new RoleManagerImpl();
    private UserDao userDao = null;
    private RoleDao roleDao = null;

    //~ Methods ================================================================
    @Before
    public void setUp() throws Exception {
        userDao = context.mock(UserDao.class);
        userManager.setUserDao(userDao);
        roleDao = context.mock(RoleDao.class);
        roleManager.setRoleDao(roleDao);
    }

    @Test
    public void testGetUser() throws Exception {
        final User testData = new User("1");
        testData.getRoles().add(new Role("user"));

        // set expected behavior on dao
        context.checking(new Expectations() {{
            one(userDao).get(with(equal(1L)));
            will(returnValue(testData));
        }});
        
        User user = userManager.getUser("1");
        assertTrue(user != null);
        assert user != null;
        assertTrue(user.getRoles().size() == 1);
    }

    @Test
    public void testSaveUser() throws Exception {
        final User testData = new User("1");
        testData.getRoles().add(new Role("user"));

        // set expected behavior on dao
        context.checking(new Expectations() {{
            one(userDao).get(with(equal(1L)));
            will(returnValue(testData));
        }});
        
        final User user = userManager.getUser("1");
        user.setPhoneNumber("303-555-1212");

        context.checking(new Expectations() {{
            one(userDao).saveUser(with(same(user)));
            will(returnValue(user));
        }});
        
        User returned = userManager.saveUser(user);
        assertTrue(returned.getPhoneNumber().equals("303-555-1212"));
        assertTrue(returned.getRoles().size() == 1);
    }

    @Test
    public void testAddAndRemoveUser() throws Exception {
        User user = new User();

        // call populate method in super class to populate test data
        // from a properties file matching this class name
        user = (User) populate(user);
        
        // set expected behavior on role dao
        context.checking(new Expectations() {{
            one(roleDao).getRoleByName(with(equal("ROLE_USER")));
            will(returnValue(new Role("ROLE_USER")));
        }});
                
        Role role = roleManager.getRole(Constants.USER_ROLE);
        user.addRole(role);

        // set expected behavior on user dao
        final User user1 = user;
        context.checking(new Expectations() {{
            one(userDao).saveUser(with(same(user1)));
            will(returnValue(user1));
        }});

        user = userManager.saveUser(user);
        assertTrue(user.getUsername().equals("john"));
        assertTrue(user.getRoles().size() == 1);

        context.checking(new Expectations() {{
            one(userDao).remove(with(equal(5L)));
        }});

        userManager.removeUser("5");

        context.checking(new Expectations() {{
            one(userDao).get(with(equal(5L)));
            will(returnValue(null));
        }});
        
        user = userManager.getUser("5");
        assertNull(user);
    }

    @Test
    public void testUserExistsException() {
        // set expectations
        final User user = new User("admin");
        user.setEmail("matt@raibledesigns.com");

        final Exception ex = new DataIntegrityViolationException("");

        context.checking(new Expectations() {{
            one(userDao).saveUser(with(same(user)));
            will(throwException(ex));
        }});
        
        // run test
        try {
            userManager.saveUser(user);
            fail("Expected UserExistsException not thrown");
        } catch (UserExistsException e) {
            log.debug("expected exception: " + e.getMessage());
            assertNotNull(e);
        }
    }
}
