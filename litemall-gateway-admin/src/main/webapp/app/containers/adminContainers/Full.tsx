import * as React from 'react';
import { Route, Routes } from 'react-router-dom';
import { Container } from 'reactstrap';

import Aside from 'app/components/adminComponents/Aside';
import Footer from '../../components/adminComponents/Footer';
import Dashboard from '../../views/adminViews/adminModule/Dashboard';

/* import Aside from '../../components/Aside/';
import Breadcrumb from '../../components/Breadcrumb/';
import Sidebar from '../../components/Sidebar/';
import Charts from '../../views/Charts/';
import Widgets from '../../views/Widgets/';
 */
// Components
/* import Buttons from '../../views/Components/Buttons/';
import Cards from '../../views/Components/Cards/';
import Forms from '../../views/Components/Forms/';
import Modals from '../../views/Components/Modals/';
import SocialButtons from '../../views/Components/SocialButtons/';
import Switches from '../../views/Components/Switches/';
import Tables from '../../views/Components/Tables/';
import Tabs from '../../views/Components/Tabs/'; */

// Icons

class Full extends React.Component {
  render() {
    return (
      <div className='app'>
        {/* <Header /> */}
        <div className='app-body'>
          {/* <Sidebar {...this.props} /> */}
          <main className='main'>
            {/* <Breadcrumb /> */}
            <Container fluid={true}>
              <Routes>
                <Route path='/dashboard' element={<Dashboard />} />
                {/* <Route path='/components/buttons' component={Buttons} /> */}
                {/* <Route path='/components/cards' component={Cards} />
                <Route path='/components/forms' component={Forms} />
                <Route path='/components/modals' component={Modals} />
                <Route path='/components/social-buttons' component={SocialButtons} /> */}
                {/* <Route path='/components/switches' component={Switches} />
                <Route path='/components/tables' component={Tables} />
                <Route path='/components/tabs' component={Tabs} />
                <Route path='/icons/font-awesome' component={FontAwesome} />
                <Route path='/icons/simple-line-icons' component={SimpleLineIcons} />
                <Route path='/widgets' component={Widgets} /> */}
                {/* <Route path='/charts' component={Charts} /> */}
                {/* <Redirect from='/' to='/dashboard' /> */}
              </Routes>
            </Container>
          </main>
          <Aside />
        </div>
        <Footer />
      </div>
    );
  }
}

export default Full;
